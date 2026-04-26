# API Reference

This document describes the HTTP surfaces exposed by PeopleMesh.
The API enforces a security- and GDPR-first posture: authenticated access, scoped authorization, protected maintenance surfaces, and explicit data-subject-rights endpoints.

---

## Authentication & Authorization

| Mechanism | Where used |
|-----------|-----------|
| OIDC session cookie | All `/api/v1/` endpoints except Auth (plus `GET /api/v1/me`, which is `@PermitAll`) |
| `@PermitAll` | Auth endpoints, `GET /api/v1/me` (returns 204 if anonymous) |
| `is_admin` entitlement | System statistics, node create/update, global skills dictionary management |
| `X-Maintenance-Key` header | All maintenance endpoints (+ optional IP/CIDR allowlist) |

API errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` responses; internal exception details are never returned in response bodies.

---

## Auth

Public endpoints for OAuth login flow.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/auth/login/{provider}` | Start OAuth login (Google, Microsoft, GitHub) |
| GET | `/api/v1/auth/callback/{provider}` | OAuth callback — sets session cookie on login success, or redirects import popups to frontend import route |
| GET | `/api/v1/auth/callback/{provider}/import-finalize` | Finalize OAuth import and return preview payload (`imported`, `source`) |
| GET | `/api/v1/auth/identity` | Lightweight current identity payload for frontend bootstrap |
| POST | `/api/v1/auth/logout` | End session — clears session cookie |

**Access:** `@PermitAll` — no session required.

`GET /api/v1/auth/login/{provider}` accepts an optional `?intent` query parameter (max 50 chars).

For `intent=profile_import`, OAuth callback behavior is two-step:
- `GET /api/v1/auth/callback/{provider}` redirects to frontend popup route `/#/oauth/import?...`.
- The frontend popup calls `GET /api/v1/auth/callback/{provider}/import-finalize` to fetch import preview data and then notifies the opener window.

---

## Info

Public bootstrap metadata for branding/legal details and OAuth provider availability.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/info` | Organization metadata and auth provider availability |

**Access:** `@PermitAll` — no session required.

`GET /api/v1/info` response includes:
- Organization fields (`organizationName`, `contactEmail`, `dpoName`, `dpoEmail`, `dataLocation`, `governingLaw`).
- `authProviders` object with:
  - `providers`: providers enabled for login.
  - `configured`: providers configured for login/import.

---

## Me (Current User)

Endpoints for the authenticated user to manage their own profile, privacy, and data.

### Profile

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/me` | Get profile |
| PUT | `/api/v1/me` | Update profile |
| PATCH | `/api/v1/me` | Partially update profile with JSON Merge Patch |
| POST | `/api/v1/me/import-apply` | Apply selected fields from an import preview |
| POST | `/api/v1/me/cv-import` | Import CV (Docling + LLM extraction) |

**Access:** `@Authenticated` (`GET /api/v1/me` is `@PermitAll` — returns 204 when anonymous).

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/me` | No query params. Returns full profile for current user or `204` when anonymous |
| `PUT /api/v1/me` | Body: `ProfileSchema` (JSON). Identity updates are limited to `identity.birth_date`; other identity fields are OAuth-managed |
| `PATCH /api/v1/me` | Body: JSON Merge Patch (`application/merge-patch+json`). RFC 7396 semantics: object keys overwrite, arrays replace fully, `null` clears mapped fields |
| `POST /api/v1/me/import-apply` | Body: partial `ProfileSchema` (JSON). `?source` (required, validated pattern) |
| `POST /api/v1/me/cv-import` | Body: multipart file upload (`multipart/form-data`, field `file`) |

`GET /api/v1/auth/identity` returns a flat payload:
- `user_id`, `provider`, `entitlements`, `display_name`, `photo_url`.

Import-apply merge behavior:
- For list fields (skills, tools, industries, languages, professional interests): the server persists the array values sent by the client — merge is client-driven.
- `professional.roles` is single-valued: import always overrides, and if multiple values are provided only the first non-blank role is stored.
- Identity imports are restricted to `identity.birth_date`; other identity fields are ignored.

### GDPR & Privacy

| Method | Path | Description |
|--------|------|-------------|
| DELETE | `/api/v1/me` | Delete account (GDPR Art. 17 — right to erasure) |
| GET | `/api/v1/me/export` | GDPR data export (Art. 15/20 — JSON attachment download) |
| GET | `/api/v1/me/consents` | List consent scopes and their current state |
| POST | `/api/v1/me/consents/{scope}` | Grant consent for a scope |
| DELETE | `/api/v1/me/consents/{scope}` | Revoke consent for a scope |
| GET | `/api/v1/me/activity` | Privacy activity dashboard |

**Access:** `@Authenticated`.

`{scope}` must match `^[a-z_]+$`.

Default consent scopes exposed by `GET /api/v1/me/consents`:
- `professional_matching`
- `embedding_processing`

### Notifications

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/me/notifications` | List notifications |

**Access:** `@Authenticated`.

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/me/notifications` | `?limit={1..100}` (default 20) |

---

## Matches

Endpoints for finding similar profiles and nodes.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/matches/prompt` | Natural-language search |
| POST | `/api/v1/matches` | Structured match from a `SearchQuery` |
| GET | `/api/v1/matches/me` | Match from a backend-built `SearchQuery` derived from the authenticated user's profile |
| GET | `/api/v1/matches/{nodeId}` | Match from a node's embedding |

**Access:** `@Authenticated`.

| Endpoint | Parameters |
|----------|-----------|
| `POST /api/v1/matches/prompt` | Body: `SearchRequest` (JSON, `query` only). Optional `?limit={1..100}`. `offset` is not supported |
| `POST /api/v1/matches` | Body: `SearchQuery` (JSON). `?type`, `?country` (validated), `?limit={1..100}`, `?offset={>=0}` |
| `GET /api/v1/matches/me` | `?type`, `?country`, `?limit={1..100}`, `?offset={>=0}` |
| `GET /api/v1/matches/{nodeId}` | `?type`, `?country`. `{nodeId}` is a UUID |

### Search matching details (`POST /api/v1/matches/prompt`)

Matching for profile results is hybrid:
- **Exact/fallback term matching** (`termsMatch`): direct or normalized skill-name overlaps.
- **Semantic matching**: query-side skill terms are matched against global skill-definition embeddings and candidate skill sets.

The candidate profile skill pool includes:
- Technical skills (`tags`)
- `tools_and_tech`
- `skills_soft`
- Normalized profile skills (`tags`, `tools_and_tech`, `skills_soft`)

Notes:
- Query parsing is LLM-first and fail-fast (invalid/unparseable structured output returns an error, with no heuristic fallback).
- `result_scope` is inferred (`all`, `people`, `jobs`, `communities`, `events`, `projects`, `groups`, `unknown`) and used for initial client intent.
- Prompt and structured matching both include geography score/reason in breakdown.
- Prompt supports only first-page bootstrap (`limit` only). Use `/api/v1/matches` with `SearchQuery` for paginated follow-up (`limit/offset`).
- Tuning reference: `peoplemesh.skills.match-threshold` in [configuration.md](configuration.md).

### My Mesh matching details (`GET /api/v1/matches/me`)

My Mesh now runs through the same `SearchService` ranking pipeline used by `/api/v1/matches`.
The backend builds a `SearchQuery` from the authenticated user's profile (roles, skills, tools, soft skills, languages, industries, seniority) and executes unified search/ranking.
Geography remains a ranking signal through the authenticated user's match context (reference country/work mode), without forcing a hard country filter unless `country` is explicitly requested.

Response shape note:
- `breakdown.commonItems` contains overlap terms for explanation.
- `breakdown.geographyReason` and `breakdown.geographyScore` are included.
- `breakdown.seniorityScore` is included for user results.
- `breakdown.mustHavePenaltyFactor` and `breakdown.negativeSkillsPenaltyFactor` are included for user results.
- Non-user results may include `breakdown.keywordScore` when keyword matching contributes to ranking.
- `breakdown` may include scoring weights (`weightEmbedding`, `weightMustHave`, `weightNiceToHave`, `weightLanguage`, `weightIndustry`, `weightGeography`, `weightSeniority`, `weightKeyword`) so clients can explain totals using backend-owned weights instead of hardcoded UI constants.
- PEOPLE results expose contact fields in camelCase (`slackHandle`, `email`, `telegramHandle`, `mobilePhone`, `linkedinUrl`).

---

## Nodes

Mesh nodes represent people, jobs, or other entities in the network.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/nodes/{nodeId}` | Get a node |
| GET | `/api/v1/nodes/{nodeId}/profile` | Public profile (read-only) |

**Access:** `@Authenticated`.

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/nodes/{nodeId}` | `?includeEmbedding=true|false` (default `false`) |

---

## Skills (Global Dictionary)

Global skills dictionary management with alias-based suggestion, CSV import, and cleanup.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/skills` | List global skills |
| GET | `/api/v1/skills/suggest` | Suggest skills by `name`/`aliases` |
| POST | `/api/v1/skills/import` | Import global skill definitions (CSV) |
| POST | `/api/v1/skills/cleanup-unused` | Delete skills where `usage_count = 0` |

**Access:** `@Authenticated`. Mutating operations require `is_admin` entitlement.

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/skills` | `?q` (optional alias/name lookup), `?page` (default 0), `?size` (default 50, max 200) |
| `GET /api/v1/skills/suggest` | `?q` (required), `?limit` (default 20, max 200) |
| `POST /api/v1/skills/import` | Body: raw CSV (`application/octet-stream`) |
| `POST /api/v1/skills/cleanup-unused` | No body. Returns deleted count |

---

## System

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/system/statistics` | Overview counters and runtime timing stats |

**Access:** `@Authenticated` + `is_admin` entitlement.

Response includes timing summaries for:

| Block | Content |
|-------|---------|
| `timings.llmInference` | LLM call latency |
| `timings.embeddingInferenceSingle` | Single-item embedding latency |
| `timings.hnswSearch` | HNSW vector search latency |

Each block provides `sampleCount`, `avgMs`, `p95Ms`, and `maxMs`.

Response also includes embedding coverage for searchable nodes:

| Block | Content |
|-------|---------|
| `searchableNodes` | Total nodes with `searchable=true` |
| `searchableNodesWithEmbedding` | Searchable nodes where `embedding IS NOT NULL` |

---

## Maintenance

Protected operations for platform operators. All endpoints require the `X-Maintenance-Key` header and may enforce IP/CIDR restrictions (see [configuration.md](configuration.md)).
The maintenance key header is validated at API boundary (`max 256` chars).

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| POST | `/api/v1/maintenance/ingest/nodes` | Body: `NodesIngestRequestDto` (JSON, validated) | Non-user node batch upsert (idempotent on `node_type` + `source` + `external_id`) |
| POST | `/api/v1/maintenance/ingest/users` | Body: `UsersIngestRequestDto` (JSON, validated) | User batch upsert (idempotent on `external_id`) |
| POST | `/api/v1/maintenance/purge-consent-tokens` | Header: `X-Maintenance-Key` | Remove expired consent tokens |
| POST | `/api/v1/maintenance/enforce-retention` | Header: `X-Maintenance-Key` | Hard-delete inactive users past retention window |
| POST | `/api/v1/maintenance/run-clustering` | Header: `X-Maintenance-Key` | Auto-discover communities (k-means) |
| POST | `/api/v1/maintenance/regenerate-embeddings` | `?nodeType`, `?onlyMissing` (default true), `?batchSize` (1..1000, default 1). Returns 202 | Start async node embedding regeneration job |
| GET | `/api/v1/maintenance/regenerate-embeddings/{jobId}` | `{jobId}` is a UUID | Poll embedding regeneration job status |
| GET | `/api/v1/maintenance/nodes` | `?type`, `?searchable` (default true), `?page` (default 0), `?size` (default 100, max 500). Returns minimal candidate payload (`id`, `nodeType`, `title`, `description`, `tags`, `country`, `structuredData`) | List candidate nodes for operational/evaluation tooling |
| POST | `/api/v1/maintenance/tuning/matches/{userId}` | Header: `X-Maintenance-Key`. Body: `MaintenanceTuningMatchesRequest` (`search_query`, optional `search_options`, optional `type`/`country`/`limit`/`offset`). `{userId}` is a UUID | Run My Mesh matching for a specific user with optional tuning overrides (`search_options`) |

Validation/configuration failures in maintenance endpoints now follow the shared validation mapping strategy and return standard `400 Bad Request` problem details.

---

## MCP

| Method | Path | Description |
|--------|------|-------------|
| POST | `/mcp` | Streamable HTTP transport |
| POST | `/mcp/sse` | Legacy SSE transport |

**Access:** Authenticated session required. MCP is read-only by design.

See [`mcp.md`](mcp.md) for available tools and integration details.

---

## Ops

| Method | Path | Description |
|--------|------|-------------|
| GET | `/q/health` | Liveness / readiness probes |
| GET | `/q/metrics` | Micrometer metrics export (format depends on enabled registry, e.g. Prometheus) |
| GET | `/q/dev-ui` | Quarkus dev dashboard (dev mode only) |

---

