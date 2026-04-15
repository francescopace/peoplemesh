# Production Baseline

This guide lists the minimum security configuration required before any non-dev deployment.

## Audience

- Platform administrators
- Security and IAM maintainers

## Required baseline

Before exposing PeopleMesh in staging or production, explicitly configure:

- OIDC provider credentials (`OIDC_<PROVIDER>_CLIENT_ID`, `OIDC_<PROVIDER>_CLIENT_SECRET`)
- Core secrets (`SESSION_SECRET`, `OAUTH_STATE_SECRET`, `CONSENT_TOKEN_SECRET`)
- Maintenance protection (`MAINTENANCE_API_KEY`, optional `MAINTENANCE_ALLOWED_CIDRS`)
- Frontend origin restrictions (`CORS_ORIGINS`)

## Recommended validation

- Verify `GET /api/v1/auth/providers` returns the expected providers.
- Verify login flow works end-to-end for at least one configured provider.
- Verify maintenance endpoints are rejected without `X-Maintenance-Key`.

For complete configuration details, see [`../reference/configuration.md`](../reference/configuration.md).
