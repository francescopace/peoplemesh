# Configuration Reference

PeopleMesh configuration is environment-driven.
In development mode, infrastructure dependencies are auto-configured via DevServices.
This reference follows PeopleMesh's security- and GDPR-first posture by design (secrets management, access restrictions, and policy-aligned data handling).

Environment variable names in this page use upper snake case (for example `DB_URL`).
Quarkus application keys use dotted lowercase format (for example `peoplemesh.search.max-per-minute`).

## Audience

- Platform administrators
- Operators and SRE
- Developers troubleshooting environment-specific behavior

## How to use this page

- Start from **Required in Production** for baseline deployment.
- Use section-specific tables for tuning and optional features.
- Confirm exact defaults in:
  - `src/main/resources/application.properties`
  - `src/main/resources/application-dev.properties`

## Required in Production

| Variable | Description |
|----------|-------------|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `OPENAI_API_KEY` | OpenAI API key |
| `CONSENT_TOKEN_SECRET` | HMAC-SHA256 signing key for consent tokens (minimum 32 bytes) |
| `SESSION_SECRET` | HMAC-SHA256 key for signed session cookies |
| `OAUTH_STATE_SECRET` | HMAC-SHA256 key for OAuth state parameters |
| `MAINTENANCE_API_KEY` | Shared secret for maintenance endpoints (`X-Maintenance-Key`) |
| `CORS_ORIGINS` | Allowed CORS origins |

At least one OIDC provider must be configured:

- `OIDC_<PROVIDER>_CLIENT_ID`
- `OIDC_<PROVIDER>_CLIENT_SECRET`

Login providers: `GOOGLE`, `MICROSOFT`.
Import-only provider: `GITHUB`.

## OIDC Provider Keys

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.oidc.google.client-id` | `none` | Google OIDC client ID |
| `peoplemesh.oidc.google.client-secret` | `none` | Google OIDC client secret |
| `peoplemesh.oidc.microsoft.client-id` | `none` | Microsoft OIDC client ID |
| `peoplemesh.oidc.microsoft.client-secret` | `none` | Microsoft OIDC client secret |
| `peoplemesh.oidc.github.client-id` | `none` | GitHub OAuth client ID (import provider) |
| `peoplemesh.oidc.github.client-secret` | `none` | GitHub OAuth client secret (import provider) |

## AI Providers

In dev mode, Ollama is used locally by default.
In production mode, OpenAI is used by default.

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | — | OpenAI API key (required in production) |
| `LLM_MODEL` | `gpt-4.1-mini` | OpenAI chat model |

Dev model defaults are configured in `application-dev.properties`:

- Chat model: `granite4:3b`
- Embedding model: `qwen3-embedding:0.6b`

## Docling / CV Import

| Variable | Default | Description |
|----------|---------|-------------|
| `DOCLING_URL` | `http://docling:5001` (prod profile) | Docling service base URL used for CV extraction |

| Key | Default | Description |
|-----|---------|-------------|
| `quarkus.docling.timeout` | `60s` | Timeout for Docling requests |
| `peoplemesh.cv-import.max-file-size` | `5242880` | Maximum CV upload size in bytes (5 MB) |

## Security and Operations

| Variable | Default | Description |
|----------|---------|-------------|
| `MAINTENANCE_ALLOWED_CIDRS` | — | Comma-separated IP/CIDR allowlist for maintenance endpoints |

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.maintenance.api-key` | — | Shared secret expected in `X-Maintenance-Key` |
| `peoplemesh.maintenance.allowed-cidrs` | — | IP/CIDR allowlist for maintenance callers |
| `peoplemesh.problems.base-uri` | `about:blank` | Base URI used in Problem Details payloads |
| `peoplemesh.consent-token.secret` | — | HMAC signing key for consent tokens |
| `peoplemesh.session.secret` | — | HMAC signing key for session cookies |
| `peoplemesh.oauth.state-secret` | — | HMAC signing key for OAuth state values |

## Search

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.search.max-per-minute` | `10` | Max searches per user per minute |
| `peoplemesh.search.min-score` | `0.05` | Minimum result score threshold |

## Matching

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.matching.candidate-pool-size` | `50` | Candidate pool size before final ranking |
| `peoplemesh.matching.result-limit` | `20` | Maximum number of returned matches |
| `peoplemesh.matching.decay-lambda` | `0.1` | Decay factor used in scoring/ranking blend |

## Skill Catalog

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.skills.reconciliation-threshold` | `0.75` | Minimum cosine similarity for fuzzy skill matching |

## Clustering

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.clustering.enabled` | `false` | Enable automatic community clustering |
| `CLUSTERING_ENABLED` | `false` | Enable automatic community clustering |
| `peoplemesh.clustering.k` | `20` | Number of clusters for k-means |
| `peoplemesh.clustering.min-cluster-size` | `5` | Minimum members for a valid cluster |
| `peoplemesh.clustering.max-centroid-distance` | `0.4` | Maximum cosine distance from centroid |

## Retention

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.retention.inactive-months` | `12` | Months of inactivity before hard deletion eligibility |

## Notifications

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.notification.enabled` | `true` | Enable notification pipeline |
| `peoplemesh.notification.dry-run` | `true` | Do not deliver externally; log/send internal flow only |
| `peoplemesh.notification.subject-prefix` | `[PeopleMesh]` | Prefix used for notification subject lines |

## Observability and Metrics (Micrometer)

PeopleMesh instruments AI and vector-search latency via Micrometer timers.
These are surfaced in admin overview (`/api/v1/system/statistics`) and can be exported through `/q/metrics`.

### Instrumented metric names

- `peoplemesh.llm.inference`
- `peoplemesh.embedding.inference`
- `peoplemesh.hnsw.search`

### Common Quarkus Micrometer keys

| Key | Default | Description |
|-----|---------|-------------|
| `quarkus.micrometer.enabled` | `true` | Enable Micrometer integration |
| `quarkus.micrometer.export.prometheus.enabled` | `false` unless Prometheus registry extension is present and enabled | Expose Prometheus-formatted metrics at `/q/metrics` |
| `quarkus.micrometer.export.otlp.enabled` | `false` unless OTLP registry extension is present and enabled | Push metrics to OTLP collectors |

For registry-specific setup (Prometheus + Grafana, OTLP, etc.), see [`../how-to/export-metrics-grafana.md`](../how-to/export-metrics-grafana.md).

## Entitlements

Entitlements are granted at login based on OAuth subject values.
Values are comma-separated OAuth subject IDs.

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.entitlements.can-create-job` | — | Subjects allowed to create jobs |
| `peoplemesh.entitlements.can-manage-skills` | — | Subjects allowed to manage skill catalogs |

## LDAP Import

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.ldap.url` | — | LDAP/LDAPS server URL |
| `peoplemesh.ldap.bind-dn` | — | Bind DN for LDAP authentication |
| `peoplemesh.ldap.bind-password` | — | Bind password |
| `LDAP_URL` | — | LDAP/LDAPS server URL |
| `LDAP_BIND_DN` | — | Bind DN for LDAP authentication |
| `LDAP_BIND_PASSWORD` | — | Bind password |
| `peoplemesh.ldap.user-base` | `ou=users,dc=example,dc=com` | LDAP search base |
| `peoplemesh.ldap.user-filter` | `(objectClass=person)` | LDAP search filter |
| `peoplemesh.ldap.page-size` | `100` | Paged query size |
| `peoplemesh.ldap.connect-timeout-seconds` | `30` | LDAP connect and response timeout in seconds |

## Session, Consent, and Error Contracts

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.consent-token.ttl-seconds` | `300` | Consent token time-to-live in seconds |
| `PROBLEMS_BASE_URI` | `about:blank` | Base URI for RFC 9457-style Problem Details responses |

## Source of truth

For exact defaults and profile-specific overrides, refer to:

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
