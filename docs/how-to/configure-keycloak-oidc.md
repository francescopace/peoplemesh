# Configure Keycloak OIDC Authentication

This guide covers setting up Keycloak as an OIDC authentication provider for PeopleMesh.

## Overview

PeopleMesh supports Keycloak alongside Google, Microsoft, and GitHub as authentication providers. Keycloak integration uses standard OIDC discovery (`.well-known/openid-configuration`) for automatic endpoint configuration.

## Prerequisites

- Running Keycloak instance (v22.0+)
- Administrative access to Keycloak realm
- PeopleMesh deployment (local or OpenShift)

## Keycloak Configuration

### 1. Create or Select a Realm

In Keycloak admin console:
1. Select or create a realm (e.g., `peoplemesh`)
2. Note the realm name for configuration

### 2. Create OIDC Client

1. Navigate to **Clients** → **Create client**
2. Configure:
   - **Client ID**: `peoplemesh` (or your choice)
   - **Client type**: OpenID Connect
   - **Client authentication**: ON
   - **Valid redirect URIs**: `https://your-peoplemesh-url/api/v1/auth/callback/keycloak`
   - **Web origins**: `https://your-peoplemesh-url`
   - **Valid post logout redirect URIs**: `https://your-peoplemesh-url`

**Important**: Disable **Proof Key for Code Exchange (PKCE)** if required by your deployment. PeopleMesh currently does not support PKCE.

### 3. Get Client Credentials

1. Go to **Clients** → your client → **Credentials** tab
2. Copy the **Client secret**

### 4. Create Test User (Optional)

```bash
# Using Keycloak admin CLI or REST API
# See tools/keycloak/create-test-user.sh for automation
```

## PeopleMesh Configuration

### Environment Variables

Set the following in your deployment:

```bash
# Keycloak OIDC configuration
OIDC_KEYCLOAK_CLIENT_ID=peoplemesh
OIDC_KEYCLOAK_CLIENT_SECRET=<your-client-secret>
OIDC_KEYCLOAK_ISSUER_URL=https://keycloak.example.com/realms/peoplemesh
```

### Via Helm (OpenShift/Kubernetes)

```yaml
secret:
  stringData:
    OIDC_KEYCLOAK_CLIENT_ID: "peoplemesh"
    OIDC_KEYCLOAK_CLIENT_SECRET: "<your-secret>"
    OIDC_KEYCLOAK_ISSUER_URL: "https://keycloak.example.com/realms/peoplemesh"
```

### Via Environment File (Local Development)

```bash
# .env
OIDC_KEYCLOAK_CLIENT_ID=peoplemesh
OIDC_KEYCLOAK_CLIENT_SECRET=your-secret
OIDC_KEYCLOAK_ISSUER_URL=http://localhost:8180/realms/peoplemesh
```

## Verification

### 1. Check Provider Availability

```bash
curl https://your-peoplemesh-url/api/v1/info | jq '.authProviders'
```

Expected output:
```json
{
  "loginProviders": ["keycloak", "google", "microsoft"],
  "profileImportProviders": []
}
```

### 2. Test Authentication Flow

1. Navigate to PeopleMesh URL
2. Click **Sign in**
3. Select **Continue with Keycloak**
4. Log in with Keycloak credentials
5. Verify successful redirect back to PeopleMesh

### 3. Test Logout

Click the logout button. You should be redirected to Keycloak's logout page and then back to PeopleMesh.

## Security Considerations

### Email Safety

**Notification System**: PeopleMesh runs in dry-run mode by default (`peoplemesh.notification.dry-run=true`). No emails are sent even if configured.

**Seed Data**: All synthetic seed data uses `@example.com` addresses, which are IANA-reserved (RFC 2606) and cannot receive email.

### Authentication vs. Data Separation

- **Authentication**: Users log in via Keycloak (or other providers)
- **Searchable Data**: Profiles in the database can be populated independently via seed data
- **Separation**: Not every profile in the database needs a login account

This separation allows:
- Test/demo environments with rich searchable data
- Production with real user authentication
- Controlled access to the application while maintaining a comprehensive talent directory

### Dev-Seed Provider Security

Seed data uses `dev-seed` as the provider value in the database. This is **not an authentication provider** - just a data label. The `dev-seed` provider:

✅ Has no OAuth implementation  
✅ Is not in the login provider whitelist  
✅ Has no OIDC endpoints configured  
✅ Cannot be used to bypass authentication  

Users with `dev-seed` provider are **searchable only** - they cannot log in.

## Troubleshooting

### Login shows "Not Implemented" error

**Cause**: Keycloak is not properly configured or environment variables not set.

**Fix**: Verify all three environment variables are set and the issuer URL is accessible.

### OAuth callback fails with 400 Bad Request

**Possible causes**:
1. **PKCE enabled**: Disable PKCE in Keycloak client settings
2. **Redirect URI mismatch**: Ensure exact match including trailing slashes
3. **Client secret incorrect**: Verify the secret matches Keycloak

### Frontend shows "No login provider configured"

**Cause**: Frontend not rebuilt after Keycloak configuration changes.

**Fix**: The application image includes the frontend compiled at build time. No rebuild needed if using the latest image.

## Advanced: Multiple Keycloak Instances

PeopleMesh supports only one Keycloak provider per deployment. For multiple realms or instances:

1. Use Keycloak realm federation
2. Or deploy separate PeopleMesh instances
3. Or use identity brokering within Keycloak

## Related Documentation

- [Seed Data Loading](../reference/seed-data.md)
- [Configure OIDC](configure-oidc.md) (Google, Microsoft, GitHub)
- [Maintenance Operations](../operations/maintenance.md)
