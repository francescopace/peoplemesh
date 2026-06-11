# Keycloak OIDC Support Implementation

## Overview
This document describes the implementation of Keycloak OIDC support in Peoplemesh, enabling authentication without requiring Google or Microsoft OAuth credentials.

## Changes Made

### 1. Configuration Layer ([AppConfig.java](src/main/java/org/peoplemesh/config/AppConfig.java))

Added a new `KeycloakProviderCreds` interface to support Keycloak-specific configuration:

```java
interface KeycloakProviderCreds {
    @WithDefault("none")
    String clientId();

    @WithDefault("none")
    String clientSecret();

    @WithDefault("none")
    String issuerUrl();
}
```

Updated `OidcProviders` interface to include Keycloak:

```java
interface OidcProviders {
    OidcProviderCreds google();
    OidcProviderCreds microsoft();
    OidcProviderCreds github();
    KeycloakProviderCreds keycloak();  // New
}
```

### 2. Application Properties ([application.properties](src/main/resources/application.properties))

Added Keycloak configuration properties:

```properties
peoplemesh.oidc.keycloak.client-id=${OIDC_KEYCLOAK_CLIENT_ID:none}
peoplemesh.oidc.keycloak.client-secret=${OIDC_KEYCLOAK_CLIENT_SECRET:none}
peoplemesh.oidc.keycloak.issuer-url=${OIDC_KEYCLOAK_ISSUER_URL:none}
```

### 3. OAuth Token Exchange Service ([OAuthTokenExchangeService.java](src/main/java/org/peoplemesh/service/OAuthTokenExchangeService.java))

#### Key Additions:

1. **OIDC Discovery Support**: Added automatic endpoint discovery via `.well-known/openid-configuration`

2. **Keycloak Exchange Method**: Implemented `exchangeKeycloak()` that:
   - Discovers OIDC endpoints dynamically
   - Exchanges authorization code for access token
   - Fetches user info from userinfo endpoint
   - Maps standard OIDC claims to `OidcSubject`

3. **Provider Registration**:
   - Added "keycloak" to `LOGIN_PROVIDERS` list
   - Updated `exchangeAndResolveSubject()` switch statement
   - Updated `buildAuthorizeUri()` to handle Keycloak
   - Updated `isProviderEnabled()` and `isLoginEnabled()`

4. **Helper Methods**:
   - `discoverKeycloakEndpoints()`: Fetches OIDC discovery document
   - `buildKeycloakAuthorizeUri()`: Constructs authorization URL from discovered endpoints
   - `isKeycloakConfigured()`: Validates Keycloak configuration

5. **Discovery Metadata Record**:
   ```java
   private record OidcDiscoveryMetadata(
       String authorizationEndpoint,
       String tokenEndpoint,
       String userinfoEndpoint
   ) {}
   ```

### 4. OAuth Login Service ([OAuthLoginService.java](src/main/java/org/peoplemesh/service/OAuthLoginService.java))

Updated provider order to include Keycloak as the first option:

```java
private static final List<String> PROVIDER_ORDER = List.of("keycloak", "google", "microsoft", "github");
```

## Implementation Highlights

### Generic OIDC Discovery
Unlike Google/Microsoft which use hardcoded endpoints, Keycloak implementation uses the standard OIDC discovery mechanism:

1. Fetches `{issuer-url}/.well-known/openid-configuration`
2. Extracts `authorization_endpoint`, `token_endpoint`, and `userinfo_endpoint`
3. Uses discovered endpoints for authorization and token exchange

This makes the implementation compatible with any standard OIDC provider (not just Keycloak).

### Standard OIDC Claims Mapping
The implementation maps standard OIDC claims from the userinfo endpoint:

- `sub` → subject ID (required)
- `name` → display name
- `given_name` → first name
- `family_name` → last name
- `email` → email address
- `locale` → user locale
- `picture` → profile picture URL

## Configuration

### Environment Variables

```bash
# Keycloak OIDC Configuration
OIDC_KEYCLOAK_CLIENT_ID=peoplemesh
OIDC_KEYCLOAK_CLIENT_SECRET=your-client-secret-here
OIDC_KEYCLOAK_ISSUER_URL=https://keycloak.example.com/realms/peoplemesh
```

### Keycloak Client Configuration

In your Keycloak realm, create a client with:

- **Client ID**: `peoplemesh` (or your chosen ID)
- **Client Protocol**: `openid-connect`
- **Access Type**: `confidential`
- **Valid Redirect URIs**: `https://peoplemesh.example.com/api/v1/auth/callback/keycloak`
- **Web Origins**: `https://peoplemesh.example.com`

### Required Scopes
- `openid` (required)
- `email` (for user email)
- `profile` (for user name and other profile info)

### Expected Token Claims

Keycloak should provide these standard claims in the userinfo endpoint:

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "email_verified": true,
  "name": "User Name",
  "given_name": "User",
  "family_name": "Name",
  "preferred_username": "username"
}
```

## Testing

### 1. Check Provider Availability

```bash
curl http://localhost:8080/api/v1/info | jq '.authProviders'
```

Expected output:
```json
{
  "loginProviders": ["keycloak", "google", "microsoft"],
  "profileImportProviders": ["github"]
}
```

### 2. Test Login Flow

1. Navigate to `http://localhost:8080/api/v1/auth/login/keycloak`
2. Should redirect to Keycloak login page
3. After authentication, should redirect back to peoplemesh
4. User session should be created

### 3. Verify OIDC Discovery

The implementation automatically discovers endpoints from:
```
https://keycloak.example.com/realms/peoplemesh/.well-known/openid-configuration
```

## Deployment

### Docker Environment Variables

```yaml
environment:
  - OIDC_KEYCLOAK_CLIENT_ID=peoplemesh
  - OIDC_KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
  - OIDC_KEYCLOAK_ISSUER_URL=https://keycloak.example.com/realms/peoplemesh
```

### Helm Chart Values

```yaml
peoplemesh:
  security:
    oidc:
      keycloak:
        clientId: "peoplemesh"
        clientSecret: "your-keycloak-client-secret"
        issuerUrl: "https://keycloak.cluster.com/realms/peoplemesh"
```

## Benefits of This Implementation

1. **No Vendor Lock-in**: Uses standard OIDC, works with any compliant provider
2. **Dynamic Discovery**: No hardcoded endpoints, adapts to Keycloak configuration
3. **Backward Compatible**: Google/Microsoft providers continue to work unchanged
4. **Minimal Configuration**: Only requires 3 environment variables
5. **Standard Compliant**: Follows OpenID Connect Core 1.0 specification

## Future Enhancements

1. **Generic OIDC Provider**: The current implementation could be generalized to support multiple custom OIDC providers beyond Keycloak
2. **Provider Display Names**: Add configurable display names for UI (e.g., "Login with SSO" instead of "Login with Keycloak")
3. **Scope Customization**: Allow configurable scopes per provider
4. **Discovery Caching**: Cache discovered endpoints to reduce latency

## References

- [Keycloak OIDC Documentation](https://www.keycloak.org/docs/latest/server_admin/#_oidc)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OIDC Discovery Specification](https://openid.net/specs/openid-connect-discovery-1_0.html)
- [Quarkus OIDC Guide](https://quarkus.io/guides/security-oidc-code-flow-authentication)
