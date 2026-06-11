# Seed Data Loading Guide

## Overview

PeopleMesh supports optional synthetic seed data for testing and demo environments. Seed data is **disabled by default** for production safety and can be enabled via the `SEED_PROFILE` environment variable.

## How It Works

**Flyway Configuration:**
```properties
# In application.properties (default)
quarkus.flyway.locations=classpath:db/migration
```

**Behavior:**
- `QUARKUS_FLYWAY_LOCATIONS` **not set** (default): Only schema migrations load (empty database)
- `QUARKUS_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/granite`: Loads schema + synthetic test data from `db/granite/`
- `QUARKUS_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/openai`: Loads schema + synthetic test data from `db/openai/`

## Seed Data Contents

Both profiles (`granite` and `openai`) contain:
- **~500 synthetic users** with skills, experience, preferences
- **~200 job postings**
- **~100 community/group definitions**
- **Skills catalog** from Stack Overflow Developer Survey

**Note:** The two profiles contain different randomly generated data sets. Choice between them is arbitrary - both are suitable for testing.

## Deployment Configuration

### **Demo/Test Environment (with seed data)**

**Via Helm values:**
```yaml
config:
  data:
    QUARKUS_FLYWAY_LOCATIONS: "classpath:db/migration,classpath:db/granite"
    # or use "classpath:db/migration,classpath:db/openai"
```

**Via environment variable:**
```bash
oc set env deployment/peoplemesh QUARKUS_FLYWAY_LOCATIONS="classpath:db/migration,classpath:db/granite"
```

**Via deploy-to-openshift.sh:**
The script automatically sets `QUARKUS_FLYWAY_LOCATIONS` to include granite seed data for convenience.

### **Production Environment (no seed data)**

**Via Helm values:**
```yaml
config:
  data:
    QUARKUS_FLYWAY_LOCATIONS: "classpath:db/migration"  # or omit entirely (default)
```

Database will start empty and populate via real user registrations.

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

**Verify QUARKUS_FLYWAY_LOCATIONS is set:**
```bash
oc set env deployment/peoplemesh -n <namespace> --list | grep QUARKUS_FLYWAY_LOCATIONS
```

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
