# Seed Data Loading Guide

## Overview

PeopleMesh supports optional synthetic seed data for testing and demo environments. Seed data is **disabled by default** for production safety.

## How It Works

**Flyway Configuration:**
```properties
# In application.properties (default - production safe)
quarkus.flyway.locations=classpath:db/migration
```

This default loads **schema only** (no seed data).

**IMPORTANT:** `quarkus.flyway.locations` is a **BUILD-TIME property** in Quarkus. The value is baked into the JAR during compilation and **cannot be changed at runtime** via environment variables.

To include seed data, you must **rebuild the application** with the property override.

## Building With Seed Data

### Option 1: Maven Build-Time Override (Recommended)

Override the property during the Maven build:

```bash
# Build with granite seed data
mvn clean package -DskipTests \
  -Dquarkus.flyway.locations=classpath:db/migration,classpath:db/granite

# Or build with openai seed data
mvn clean package -DskipTests \
  -Dquarkus.flyway.locations=classpath:db/migration,classpath:db/openai
```

**For container builds**, modify your build script to include the `-D` flag:

```bash
# In build-and-push.sh or similar
mvn clean package -DskipTests --batch-mode --no-transfer-progress \
  -Dquarkus.flyway.locations=classpath:db/migration,classpath:db/granite
```

### Option 2: Modify application.properties

For permanent seed data inclusion (not recommended for upstream), edit `src/main/resources/application.properties`:

```properties
# NOT recommended for upstream repository
quarkus.flyway.locations=classpath:db/migration,classpath:db/granite
```

Then rebuild normally:
```bash
mvn clean package -DskipTests
```

**Note:** Option 1 is preferred because it keeps the upstream repository production-safe while allowing quickstart/demo builds to include seed data.

## Seed Data Profiles

**Available seed data sets:**
- **`classpath:db/granite`**: Synthetic data generated with IBM Granite models
- **`classpath:db/openai`**: Synthetic data generated with OpenAI models

## Seed Data Contents

Both profiles (`granite` and `openai`) contain:
- **~500 synthetic users** with skills, experience, preferences
- **~200 job postings**
- **~100 community/group definitions**
- **Skills catalog** from Stack Overflow Developer Survey

**Note:** The two profiles contain different randomly generated data sets. Choice between them is arbitrary - both are suitable for testing.

## Deployment

### **Demo/Test Environment (with seed data)**

Deploy an image built with seed data (see "Building With Seed Data" above):

```bash
# Deploy container image that was built with seed data included
helm install peoplemesh ./charts/peoplemesh \
  --set image.repository=quay.io/myorg/peoplemesh \
  --set image.tag=with-seed-data
```

The database will populate with ~500 synthetic users on first startup.

### **Production Environment (no seed data)**

Deploy the default image (built without seed data override):

```bash
# Deploy container image built with default configuration
helm install peoplemesh ./charts/peoplemesh \
  --set image.repository=quay.io/myorg/peoplemesh \
  --set image.tag=latest
```

Database will start empty and populate via real user registrations.

### **Image Tagging Strategy**

Consider using distinct image tags to identify builds:
- `latest` or `vX.Y.Z` - production builds (no seed data)
- `demo` or `with-granite-seeds` - demo builds (includes seed data)
- `vX.Y.Z-granite` - versioned demo builds

## Post-Deployment: Generate Embeddings

After deploying with seed data, you **must** generate embeddings for semantic search to work:

### **Using the pmc CLI:**

```bash
# Get credentials
ROUTE=$(oc get route peoplemesh -n <namespace> -o jsonpath='{.spec.host}')
MAINT_KEY=$(oc get secret peoplemesh-secrets -n <namespace> -o jsonpath='{.data.MAINTENANCE_API_KEY}' | base64 -d)

# Generate embeddings for all seed data
./pmc regenerate-embeddings \
  --base-url "https://${ROUTE}" \
  --maintenance-key "${MAINT_KEY}" \
  --only-missing \
  --batch-size 4

# Monitor progress (pmc will poll automatically)
# Or check manually:
./pmc regenerate-embeddings-status \
  --base-url "https://${ROUTE}" \
  --maintenance-key "${MAINT_KEY}" \
  --job-id <uuid-from-previous-command>
```

### **Expected Time:**
- **~500 users** with batch size 4 = ~125 batches
- **CPU-based embedding model**: ~5-10 minutes total
- **GPU-based embedding model**: ~2-5 minutes total

## Verification

Check that seed data loaded:

```bash
# Check user count
oc exec -n <namespace> pgvector-0 -- \
  psql -U peoplemesh -d peoplemesh -c \
  "SELECT COUNT(*) FROM mesh.mesh_node WHERE node_type = 'USER';"

# Check skills catalog
oc exec -n <namespace> pgvector-0 -- \
  psql -U peoplemesh -d peoplemesh -c \
  "SELECT COUNT(*) FROM skills.skill_definition;"

# Check embedding status (via admin UI or API)
curl -s "https://${ROUTE}/api/v1/admin/statistics" | jq '.searchableNodesWithEmbedding'
```

Expected results:
- Users: ~500
- Skills: ~2000+
- Embeddings: Should match user count after regeneration completes

## Troubleshooting

### Seed data didn't load

**Check Flyway logs:**
```bash
oc logs deployment/peoplemesh -n <namespace> | grep -i flyway
```

Should show: `Successfully validated X migrations` where X > 3 (schema + seed files)

**Verify the image was built with seed data:**

The application startup logs will show which Flyway locations are active. If you only see migrations from `classpath:db/migration`, the image was built without seed data.

**Solution:** Rebuild the image with the `-Dquarkus.flyway.locations` override (see "Building With Seed Data" above).

**Note:** Setting `QUARKUS_FLYWAY_LOCATIONS` as an environment variable at runtime has **no effect** because this is a build-time property.

### Embeddings generation fails

**Check Ollama/embedding service is running:**
```bash
oc get pods -n <namespace> | grep ollama
```

**Check embedding model is pulled:**
```bash
oc exec statefulset/peoplemesh-ollama -n <namespace> -- ollama list
```

Should show your configured embedding model (e.g., `granite-embedding:30m`)

**Check application logs:**
```bash
oc logs deployment/peoplemesh -n <namespace> --tail=100 | grep -i embedding
```

## Security Notes

1. **Never enable seed data in production** - it contains fake user accounts
2. All seed users have `@example.com` emails (IANA-reserved, cannot receive email)
3. Seed data provider is marked as `dev-seed` (not a real OAuth provider)
4. `R__` prefix means repeatable - Flyway re-runs on every deployment if enabled
5. For production, use real user registration or one-time data migration scripts

## Related Documentation

- [Maintenance Operations](docs/operations/maintenance.md)
- [Regenerate Node Embeddings](docs/how-to/regenerate-node-embeddings.md)
- [API Reference](docs/reference/api.md)
