# API Reference

This document describes the HTTP surfaces exposed by PeopleMesh.
The API enforces a security- and GDPR-first posture: authenticated access, scoped authorization, protected maintenance surfaces, and explicit data-subject-rights endpoints.

## Audience

- Application developers integrating with PeopleMesh
- Platform operators validating endpoint contracts
- Security reviewers auditing access controls

## How to use this page

- Use this page as the authoritative lookup for paths, parameters, and access rules.
- Use [`../how-to/README.md`](../how-to/README.md) for procedural tasks.
- Use [`mcp.md`](mcp.md) for MCP-specific details.
- Use [`configuration.md`](configuration.md) for tuning parameters referenced here.

---

## Authentication & Authorization

| Mechanism | Where used |
|-----------|-----------|
| OIDC session cookie | All `/api/v1/` endpoints except Auth (plus `GET /api/v1/me`, which is `@PermitAll`) |
| `@PermitAll` | Auth endpoints, `GET /api/v1/me` (returns 204 if anonymous) |
| `is_admin` entitlement | System statistics, node create/update, skill catalog management |
| `X-Maintenance-Key` header | All maintenance endpoints (+ optional IP/CIDR allowlist) |

API errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` responses; internal exception details are never returned in response bodies.

---

## Auth

Public endpoints for OAuth login flow.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/auth/providers` | List enabled OAuth providers |
| GET | `/api/v1/auth/login/{provider}` | Start OAuth login (Google, Microsoft, GitHub) |
| GET | `/api/v1/auth/callback/{provider}` | OAuth callback — sets session cookie on success |
| POST | `/api/v1/auth/logout` | End session — clears session cookie |

**Access:** `@PermitAll` — no session required.

`GET /api/v1/auth/login/{provider}` accepts an optional `?intent` query parameter (max 50 chars).

---

## Me (Current User)

Endpoints for the authenticated user to manage their own profile, privacy, and data.

### Profile

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/me` | Get profile |
| PUT | `/api/v1/me` | Update profile |
| POST | `/api/v1/me/import-apply` | Apply selected fields from an import preview |
| POST | `/api/v1/me/cv-import` | Import CV (Docling + LLM extraction) |

**Access:** `@Authenticated` (`GET /api/v1/me` is `@PermitAll` — returns 204 when anonymous).

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/me` | `?identity_only=true` — lightweight session check, returns identity payload only |
| `PUT /api/v1/me` | Body: `ProfileSchema` (JSON). Identity updates are limited to `identity.birth_date`; other identity fields are OAuth-managed |
| `POST /api/v1/me/import-apply` | Body: partial `ProfileSchema` (JSON). `?source` (required, validated pattern) |
| `POST /api/v1/me/cv-import` | Body: multipart file upload (`multipart/form-data`, field `file`) |

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

### Skills & Notifications

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/me/skills` | List skill self-assessments |
| PUT | `/api/v1/me/skills` | Update skill self-assessments |
| GET | `/api/v1/me/notifications` | List notifications |

**Access:** `@Authenticated`.

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/me/skills` | `?catalog_id={uuid}` — filter by catalog |
| `PUT /api/v1/me/skills` | Body: JSON array of `SkillAssessmentDto` (max 500 items) |
| `GET /api/v1/me/notifications` | `?limit={1..100}` (default 20) |

### Profile field reference

| Field path | Notes |
|-----------|-------|
| `identity.birth_date` | Only identity field editable via API |
| `contacts.slack_handle` | Contact field |
| `contacts.telegram_handle` | Contact field |
| `contacts.mobile_phone` | Contact field |
| `contacts.linkedin_url` | Contact field |

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
| `POST /api/v1/matches/prompt` | Body: `SearchRequest` (JSON, `query` only). `?limit={1..100}`, `?offset={>=0}` |
| `POST /api/v1/matches` | Body: `SearchQuery` (JSON). `?type`, `?country` (validated), `?limit={1..100}`, `?offset={>=0}` |
| `GET /api/v1/matches/me` | `?type`, `?country`, `?limit={1..100}`, `?offset={>=0}` |
| `GET /api/v1/matches/{nodeId}` | `?type`, `?country`. `{nodeId}` is a UUID |

### Search matching details (`POST /api/v1/matches/prompt`)

Matching for profile results is hybrid:
- **Exact/fallback term matching** (`termsMatch`): direct or normalized skill-name overlaps.
- **Semantic matching**: query-side skill terms are matched against catalog skill definition embeddings and candidate skill sets.

The candidate profile skill pool includes:
- Technical skills (`tags`)
- `tools_and_tech`
- `skills_soft`
- Resolved catalog assessment skill names (when present)

Response `breakdown` fields per result:
- `matchedMustHaveSkills` — query-side must-have skills considered matched
- `matchedNiceToHaveSkills` — query-side nice-to-have skills considered matched
- `missingMustHaveSkills` — query-side must-have skills not matched
- `geographyScore` — geography compatibility contribution used in ranking
- `geographyReason` — human-readable geography reason code (for example `same_country`, `same_continent`, `remote_friendly`)

Query parsing:
- Preferred: LLM parser (`must_have`, `nice_to_have`, `keywords`, `embedding_text`)
- Fallback: token parser with role/language/context separation
- `result_scope` indicates inferred result intent (`all`, `people`, `jobs`, `communities`, `events`, `projects`, `groups`, `unknown`) and drives initial Search tab auto-selection; defaults to `unknown` when absent.

Country filter behavior:
- Prompt search derives country hard-filtering from parsed `must_have.location` when a country is clearly mappable to an ISO code.
- `SearchRequest` for prompt search contains only `query`; country filtering is parser-driven.
- Structured matches (`POST /api/v1/matches`) accept a direct `SearchQuery` body and reuse prompt-scoring logic for follow-up filtered requests without reparsing via LLM.
- Prompt and structured matching both include geography scoring in result breakdown.
- Prompt and schema match endpoints support `limit/offset`; if omitted, server defaults are applied (`limit=20`, `offset=0`).
- In the current web UI (`search` view):
  - the initial request is `POST /api/v1/matches/prompt?limit=10&offset=0`
  - subsequent type/country filter changes use `POST /api/v1/matches?type=...&country=...&limit=10&offset=0` with `SearchQuery` as body (keeps `/matches` flow and preserves prompt scoring on backend)
  - `Load more` uses progressive `offset` calls (`offset += page_size`) against the active endpoint
  - the filter bar stays visible even when a search page returns zero results (to allow immediate filter changes)

Tuning: see `peoplemesh.search.skill-match-threshold` in [configuration.md](configuration.md).

Response shape highlights:
- `POST /api/v1/matches/prompt` returns `SearchResponse` with:
  - `parsedQuery`
  - `results[]` (`SearchResultItem`)
- For profile results (`resultType = "profile"`), `SearchResultItem` includes contact fields in camelCase:
  - `slackHandle`, `email`, `telegramHandle`, `mobilePhone`, `linkedinUrl`
- For node results (`resultType = "node"`), profile-only fields are `null`; node fields (`nodeType`, `title`, `description`, `tags`) are populated.

### My Mesh matching details (`GET /api/v1/matches/me`)

My Mesh now runs through the same `SearchService` ranking pipeline used by `/api/v1/matches`.
The backend builds a `SearchQuery` from the authenticated user's profile (roles, skills, tools, soft skills, languages, industries, location) and executes unified search/ranking.

Response shape note:
- `breakdown.commonItems` contains matched overlap terms used for card highlighting and explanation.
- `breakdown.geographyReason` and `breakdown.geographyScore` are propagated from unified search breakdown.
- PEOPLE results include `person` details with contact fields in camelCase:
  - `person.slackHandle`, `person.email`, `person.telegramHandle`, `person.mobilePhone`, `person.linkedinUrl`
- Input/storage naming can still use snake_case in profile structured data (for example `contacts.linkedin_url` / `linkedin_url`), while match/search API responses use camelCase.

Current web UI behavior (`My Mesh` / `explore` view):
- default tab is `People`
- changing country triggers a new `GET /api/v1/matches/me?country=...` request
- changing type triggers a new `GET /api/v1/matches/me?type=...` request
- pagination is backend-driven (`limit=9`, progressive `offset`) with `Load more`

---

## Nodes

Mesh nodes represent people, jobs, or other entities in the network.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/nodes` | List nodes |
| POST | `/api/v1/nodes` | Create a node |
| GET | `/api/v1/nodes/{nodeId}` | Get a node |
| PUT | `/api/v1/nodes/{nodeId}` | Update a node |
| GET | `/api/v1/nodes/{nodeId}/skills` | Node skill assessments |
| GET | `/api/v1/nodes/{nodeId}/profile` | Public profile (read-only) |

**Access:** `@Authenticated`. Create and update require `is_admin` entitlement.

| Endpoint | Parameters |
|----------|-----------|
| `GET /api/v1/nodes` | `?type` — filter by node type (alphabetic pattern) |
| `POST /api/v1/nodes` | Body: JSON. Cannot create `JOB` nodes — jobs are managed via `POST /api/v1/maintenance/ingest/jobs` |
| `GET /api/v1/nodes/{nodeId}/skills` | `?catalog_id={uuid}` — filter by catalog |

---

## Skills (Catalogs)

Skill catalog management — definitions, categories, and CSV import.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/skills` | List catalogs |
| POST | `/api/v1/skills` | Create a catalog |
| GET | `/api/v1/skills/{catalogId}` | Get a catalog |
| PUT | `/api/v1/skills/{catalogId}` | Update a catalog |
| DELETE | `/api/v1/skills/{catalogId}` | Delete a catalog |
| POST | `/api/v1/skills/{catalogId}/import` | Import skill definitions (CSV) |
| GET | `/api/v1/skills/{catalogId}/definitions` | List skill definitions |
| GET | `/api/v1/skills/{catalogId}/categories` | List categories |

**Access:** `@Authenticated`. Mutating operations require `is_admin` entitlement.

| Endpoint | Parameters |
|----------|-----------|
| `POST /api/v1/skills` | Body: JSON. Returns 201 |
| `PUT /api/v1/skills/{catalogId}` | Body: JSON |
| `DELETE /api/v1/skills/{catalogId}` | Returns 204 |
| `POST /api/v1/skills/{catalogId}/import` | Body: raw CSV (`application/octet-stream`) |
| `GET /api/v1/skills/{catalogId}/definitions` | `?category`, `?page` (default 0), `?size` (default 50, max 200) |

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
| `timings.embeddingInferenceBatch` | Batch embedding latency |
| `timings.hnswSearch` | HNSW vector search latency |

Each block provides `sampleCount`, `avgMs`, `p95Ms`, and `maxMs`.

---

## Maintenance

Protected operations for platform operators. All endpoints require the `X-Maintenance-Key` header and may enforce IP/CIDR restrictions (see [configuration.md](configuration.md)).

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/maintenance/ingest/jobs` | ATS job batch upsert (idempotent on `external_id`) |
| POST | `/api/v1/maintenance/purge-consent-tokens` | Remove expired consent tokens |
| POST | `/api/v1/maintenance/enforce-retention` | Hard-delete inactive users past retention window |
| POST | `/api/v1/maintenance/run-clustering` | Auto-discover communities (k-means) |
| POST | `/api/v1/maintenance/regenerate-embeddings` | Start async node embedding regeneration job |
| GET | `/api/v1/maintenance/regenerate-embeddings/{jobId}` | Poll embedding regeneration job status |
| POST | `/api/v1/maintenance/ldap-import/preview` | LDAP import preview |
| POST | `/api/v1/maintenance/ldap-import` | Execute LDAP import |

| Endpoint | Parameters |
|----------|-----------|
| `POST .../ingest/jobs` | Body: `AtsIngestRequestDto` (JSON, validated) |
| `POST .../regenerate-embeddings` | `?nodeType`, `?onlyMissing` (default true), `?batchSize` (1..1000, default 1). Returns 202 |
| `GET .../regenerate-embeddings/{jobId}` | `{jobId}` is a UUID |
| `POST .../ldap-import/preview` | `?limit` (1..200, default 20) |

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

## Implementation notes

- REST endpoints live under `org.peoplemesh.api.resource`.
- Orchestration follows `api/resource → service → repository`.
- MCP transport follows `mcp → service → repository` (read-only).
- API error contracts and exception mapping live under `org.peoplemesh.api.error`.
- Input validation uses Jakarta Validation on DTOs and endpoint payloads.
