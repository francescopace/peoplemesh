# Configure LDAP Import

Connect an LDAP/LDAPS directory and run recurring user import into PeopleMesh.

## Why this matters

Controlled LDAP import helps align identity data flows with core GDPR principles:

- [GDPR Art. 5(1)(c)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): data minimization through scoped filters and paging.
- [GDPR Art. 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj): secure processing with access controls and protected maintenance endpoints.

## Audience

- Platform administrators
- Identity integration maintainers

## Prerequisites

- Reachable LDAP/LDAPS endpoint
- Bind DN and bind password with directory read permissions
- Maintenance API key (`MAINTENANCE_API_KEY`)
- Source IP allowed by `MAINTENANCE_ALLOWED_CIDRS` (if configured)

## Procedure

1. Configure LDAP settings:
   - `LDAP_URL`
   - `LDAP_BIND_DN`
   - `LDAP_BIND_PASSWORD`
   - `peoplemesh.ldap.user-base` (optional override)
   - `peoplemesh.ldap.user-filter` (optional override)
   - `peoplemesh.ldap.page-size` (optional override)
2. Configure maintenance access:
   - `MAINTENANCE_API_KEY`
   - `MAINTENANCE_ALLOWED_CIDRS` (optional)
3. Restart the application after configuration updates.
4. Run a CLI smoke test preview:
   - `./pmc ldap-preview --limit 20 --base-url http://localhost:8080`
5. Validate preview payload and expected users.
6. Run the LDAP import via CLI:
   - `./pmc ldap-import --base-url http://localhost:8080`
7. Configure recurring import scheduling.
8. After import, regenerate missing user embeddings:
   - `./pmc regenerate-embeddings --node-type USER --only-missing --base-url http://localhost:8080`
   - for full options and troubleshooting, see [`regenerate-node-embeddings.md`](regenerate-node-embeddings.md)

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- Imported users are visible as nodes in PeopleMesh.
- The preview endpoint returns expected directory entries.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- Connection failures: verify LDAP network access, certificate chain, and URL scheme.
- Empty preview results: review base DN and filter correctness.
- Partial imports: decrease `peoplemesh.ldap.page-size` and retry in smaller batches.

Note: This guide provides operational implementation guidance and is not legal advice.
