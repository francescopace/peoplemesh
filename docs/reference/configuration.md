# Configuration Reference

PeopleMesh configuration is environment-driven.
In development mode, infrastructure dependencies are auto-configured via DevServices.
This reference follows PeopleMesh's security- and GDPR-first posture by design (secrets management, access restrictions, and policy-aligned data handling).

Environment variable names in this page use upper snake case (for example `DB_URL`).
Quarkus application keys use dotted lowercase format (for example `peoplemesh.skills.match-threshold`).

## Audience

- Platform administrators
- SRE/DevOps operators
- Developers troubleshooting environment-specific behavior

## How to Use This Page

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
In production mode, Granite via Ollama is used by default.

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_BASE_URL` | `http://ollama:11434` | Ollama base URL used in production profile |
| `LLM_MODEL` | `granite4:3b` | Granite chat model used for query parsing and CV extraction prompts in production |
| `EMBEDDING_MODEL` | `granite-embedding:30m` | Granite embedding model used for vector generation in production |
| `OPENAI_API_KEY` | — | Optional OpenAI API key when explicitly switching provider/model |

Dev model defaults are configured in `application-dev.properties`:

- Chat model: `granite4:3b`
- Embedding model: `granite-embedding:30m`

## Docling / CV Import

| Variable | Default | Description |
|----------|---------|-------------|
| `DOCLING_BASE_URL` | `http://docling:5001` (prod profile) | Canonical Docling service base URL used for CV extraction |

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
| `peoplemesh.frontend.enabled` | `false` | Enable serving static frontend resources from backend |
| `peoplemesh.frontend.dir` | `src/main/web` | Filesystem directory used to serve frontend static files when enabled |

## Persistence and Database Tuning

The defaults below are tuned for a balanced dev/prod baseline and can be adjusted with load tests.

| Key | Default | Description |
|-----|---------|-------------|
| `quarkus.datasource.jdbc.min-size` | `3` | Minimum JDBC connections kept in pool |
| `quarkus.datasource.jdbc.max-size` | `30` | Maximum JDBC connections in pool |
| `quarkus.datasource.jdbc.acquisition-timeout` | `10S` | Max wait for a JDBC connection from pool |
| `quarkus.hibernate-orm.jdbc.statement-batch-size` | `50` | JDBC batch size for write-heavy operations |
| `quarkus.hibernate-orm.jdbc.statement-fetch-size` | `100` | JDBC fetch size hint for read-heavy operations |

## Search

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.search.candidate-pool-size` | `100` | Candidate pool size fetched from vector search before unified search rescoring and pagination |

Search matching notes:

- Prompt search uses a hybrid strategy:
  - exact/fallback term matching (`termsMatch`)
  - semantic matching over global skill dictionary embeddings
- My Mesh (`GET /api/v1/matches/me`) uses the same `SearchService` pipeline as Search endpoints, with a backend-built `SearchQuery` derived from the current profile.
- `peoplemesh.search.candidate-pool-size` tunes how many DB candidates are fetched before Java-side rescoring.
- `peoplemesh.skills.match-threshold` tunes the semantic gate used by search and matching (higher = stricter precision, lower = higher recall).
- `peoplemesh.skills.reconciliation-threshold` is reserved for dictionary reconciliation/dedup workflows.

## Matching

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.matching.candidate-pool-size` | `100` | Candidate pool size before final ranking |

## Global Skills Dictionary

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.skills.match-threshold` | `0.80` | Minimum semantic similarity for query-skill to candidate-skill matching in the unified search/matching pipeline |
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
| `peoplemesh.entitlements.is-admin` | — | Subjects granted admin access (`is_admin`) |

## Organization Identity

These values populate the Privacy Policy and Terms of Service pages with organization-specific details.
When not set, legal pages display generic text (e.g. "the Operator").

| Variable | Key | Default | Description |
|----------|-----|---------|-------------|
| `ORGANIZATION_NAME` | `peoplemesh.organization.name` | — | Organisation name; shown in legal pages and browser title ("PeopleMesh \| Name") |
| `ORGANIZATION_CONTACT_EMAIL` | `peoplemesh.organization.contact-email` | — | Contact email shown in legal pages |
| `ORGANIZATION_DPO_NAME` | `peoplemesh.organization.dpo-name` | — | Data Protection Officer name |
| `ORGANIZATION_DPO_EMAIL` | `peoplemesh.organization.dpo-email` | — | Data Protection Officer email |
| `ORGANIZATION_DATA_LOCATION` | `peoplemesh.organization.data-location` | — | Where user data is stored (e.g. "EU, Frankfurt") |
| `ORGANIZATION_GOVERNING_LAW` | `peoplemesh.organization.governing-law` | — | Governing law for Terms of Service (e.g. "Italian law, courts of Milan") |

Exposed publicly via `GET /api/v1/info` (no authentication required).

## Session, Consent, and Error Contracts

| Key | Default | Description |
|-----|---------|-------------|
| `peoplemesh.consent-token.ttl-seconds` | `300` | Consent token time-to-live in seconds |
| `PROBLEMS_BASE_URI` | `about:blank` | Base URI for RFC 9457-style Problem Details responses |

## Source of truth

For exact defaults and profile-specific overrides, refer to:

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
