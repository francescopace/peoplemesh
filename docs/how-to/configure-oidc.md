# Configure OIDC Providers

Set up one or more OIDC identity providers for authentication in PeopleMesh.

## Why this matters

Strong authentication setup is a baseline control for personal-data protection:

- [GDPR Art. 25](https://eur-lex.europa.eu/eli/reg/2016/679/oj): data protection by design and by default.
- [GDPR Art. 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj): appropriate security controls for access to personal data.

## Audience

- Platform administrators
- Security and IAM maintainers

## Prerequisites

- Access to OIDC client credentials for at least one provider
- Ability to set environment variables in the deployment environment
- Application restart capability

## Create apps and retrieve credentials (supported providers)

PeopleMesh currently supports these providers:

- Google (`google`)
- Microsoft (`microsoft`)
- GitHub (`github`)

For each provider, create an OAuth/OIDC application, configure the callback URI, then copy `client_id` and `client_secret`.

For company login, configure the provider your organization uses (typically Google or Microsoft).
GitHub is used to retrieve profile/import data and is not used as the primary authentication server.

### Google

1. In Google Cloud Console, create/select a project and open **APIs & Services** -> **Credentials**.
2. Create an **OAuth client ID** (web application).
3. Set the authorized redirect URI to:
   - `<your-peoplemesh-base-url>/api/v1/auth/callback/google`
4. Copy the generated client ID and client secret.
5. Store them as:
   - `OIDC_GOOGLE_CLIENT_ID`
   - `OIDC_GOOGLE_CLIENT_SECRET`

### Microsoft

1. In Microsoft Entra admin center, open **App registrations** and create a new registration.
2. Add a **Web** redirect URI:
   - `<your-peoplemesh-base-url>/api/v1/auth/callback/microsoft`
3. Create a client secret under **Certificates & secrets**.
4. Copy the application (client) ID and the client secret value.
5. Store them as:
   - `OIDC_MICROSOFT_CLIENT_ID`
   - `OIDC_MICROSOFT_CLIENT_SECRET`

### GitHub

Use GitHub only for profile/import integration (not as a login provider).

1. In GitHub settings, create a new **OAuth App**.
2. Set the authorization callback URL to:
   - `<your-peoplemesh-base-url>/api/v1/auth/callback/github`
3. Generate the client secret.
4. Copy the client ID and client secret.
5. Store them as:
   - `OIDC_GITHUB_CLIENT_ID`
   - `OIDC_GITHUB_CLIENT_SECRET`

## Procedure

1. Configure at least one login provider using:
   - `OIDC_GOOGLE_CLIENT_ID` and `OIDC_GOOGLE_CLIENT_SECRET`, or
   - `OIDC_MICROSOFT_CLIENT_ID` and `OIDC_MICROSOFT_CLIENT_SECRET`
2. Optionally configure GitHub import provider:
   - `OIDC_GITHUB_CLIENT_ID`
   - `OIDC_GITHUB_CLIENT_SECRET`
3. Ensure required security secrets are configured:
   - `SESSION_SECRET`
   - `OAUTH_STATE_SECRET`
4. Set `CORS_ORIGINS` for your frontend domains.
5. Restart the application.
6. CLI applicability:
   - Not applicable for this guide (OIDC is configuration-only; no maintenance endpoint action is invoked).

## Verification

- Call `GET /api/v1/auth/providers` and verify expected providers are listed.
- Run one full login flow (`/api/v1/auth/login/{provider}` -> callback).
- Verify authenticated access to `GET /api/v1/me`.

## Troubleshooting

- Provider not listed: check client ID/secret presence and environment injection.
- OAuth callback errors: verify redirect URI configuration in provider console.
- Session issues after login: confirm `SESSION_SECRET` and cookie/security headers.

Note: This guide provides operational implementation guidance and is not legal advice.
