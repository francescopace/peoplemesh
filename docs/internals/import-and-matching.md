# Import and Matching Architecture

This page describes how profile import and My Mesh matching work at an architecture level.

Use this page to understand responsibilities, flow boundaries, and ranking behavior.
For endpoint contracts, use [`../reference/api.md`](../reference/api.md).
For configuration/tuning keys, use [`../reference/configuration.md`](../reference/configuration.md).

## Audience

- Contributors onboarding to import or matching internals
- Maintainers investigating scoring behavior and result explanations
- Reviewers validating layer boundaries across API, service, and repository

## How to Use This Page

- Start from `Profile Import Pipelines` to understand ingestion and confirmation boundaries.
- Use `My Mesh Matching Pipeline` for ranking and response behavior.
- Use `Boundaries and Responsibilities` to validate layer ownership during refactors.

## Scope

This is an internal architecture page, not a procedural runbook.
For step-by-step setup and operations, use pages under `docs/how-to/`.

## Profile Import Pipelines (CV + GitHub)

PeopleMesh supports two user-initiated import paths that prefill profile data before explicit apply.

### CV Import Flow

1. User uploads a CV to `POST /api/v1/me/cv-import`.
2. Backend sends document content to Docling for parsing.
3. Parsed content is structured into profile fields via LLM extraction.
4. Backend returns an import preview payload.
5. User selects fields and confirms through `POST /api/v1/me/import-apply`.
6. Only confirmed fields are persisted.

Notes:

- CV import is explicit and user-driven (not an automatic background process).
- Original uploaded files are not persisted as profile data.
- Provider/model behavior depends on environment configuration.

### GitHub Import Flow

1. User starts GitHub OAuth import from the UI.
2. Backend exchanges OAuth credentials for provider access.
3. Backend fetches GitHub profile plus repository enrichment data.
4. OAuth callback redirects the popup to a frontend route (`/#/oauth/import?...`) with provider/code/state parameters.
5. Frontend popup calls `GET /api/v1/auth/callback/{provider}/import-finalize`.
6. Backend builds and returns import preview payload (`imported`, `source`).
7. Popup posts the import payload to the opener window.
8. User reviews and confirms selected fields.
9. Confirmed fields are applied via `POST /api/v1/me/import-apply`.

Notes:

- GitHub acts as an import provider in this flow.
- Imported data is previewed before persistence.
- Provider setup is documented in [`../how-to/configure-oidc.md`](../how-to/configure-oidc.md).

## Global Skills Dictionary

PeopleMesh uses a single global `skills.skill_definition` table as the normalization backbone:

- `name`: canonical normalized skill value
- `aliases`: display variants and synonyms used by UI suggestion
- `usage_count`: synchronous profile reference counter
- `embedding`: semantic vector for skill similarity

On profile save (`PUT /api/v1/me`, `PATCH /api/v1/me`, and import-apply), the backend:

1. Reads previous profile skill set (`tags`, `skills_soft`, `tools_and_tech`).
2. Normalizes incoming skill terms through dictionary name/alias lookup.
3. Upserts unknown skills with `usage_count = 0`.
4. Computes added/removed skill diff and updates counters in the same transaction.
5. Persists normalized profile values before building embedding text.

## My Mesh Matching Pipeline

Endpoint: `GET /api/v1/matches/me`

My Mesh reuses the same unified search/ranking path used by search endpoints.

### End-to-End Behavior

1. Backend reads authenticated user profile context.
2. Backend builds a `SearchQuery` from roles, skills, tools, soft skills, languages, industries, and location.
3. Search service runs candidate retrieval and hybrid scoring.
4. Results are filtered by requested dimensions (`type`, `country`, pagination).
5. Response returns ranked items plus breakdown fields used by UI explanations.

### Ranking Model (High Level)

Hybrid scoring combines:

- exact/fallback term overlap (`termsMatch`)
- semantic similarity over skill embeddings
- geography contribution (`geographyScore` + `geographyReason`)
- seniority contribution (`seniorityScore`)

User-score penalties are also modeled explicitly in breakdown payloads:

- `mustHavePenaltyFactor`
- `negativeSkillsPenaltyFactor`

The breakdown can also expose per-signal weights (`weightEmbedding`, `weightMustHave`, `weightNiceToHave`, `weightLanguage`, `weightIndustry`, `weightGeography`, `weightSeniority`, `weightKeyword`) so clients can reconstruct explanation totals without duplicating scoring constants.

`industryScore` is included in breakdown and its current user-ranking weight is `0.10`.

For non-user results ranked through the unified search pipeline, breakdown may also include `keywordScore` with its corresponding `weightKeyword`.

## Configuration Knobs

Primary keys affecting matching quality and ranking behavior:

- `peoplemesh.search.candidate-pool-size`
- `peoplemesh.skills.match-threshold`
- `peoplemesh.matching.candidate-pool-size`

See full key reference in [`../reference/configuration.md`](../reference/configuration.md).

## Boundaries and Responsibilities

- API/resource layer handles transport concerns.
- Service layer orchestrates import, query building, and scoring.
- Repository layer owns data access and query details.
- Matching domain logic is reused across REST and MCP entrypoints.

For broader system context, see [`architecture.md`](architecture.md).
