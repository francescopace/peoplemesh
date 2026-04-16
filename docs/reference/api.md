# API Reference

This document lists the main HTTP surfaces exposed by PeopleMesh.
This reference follows PeopleMesh's security- and GDPR-first posture by design (authenticated access, scoped authorization, protected maintenance surfaces, and explicit data-subject-rights endpoints).

## Audience

- Application developers
- Integrators
- Platform operators validating endpoint contracts

## How to use this page

- Use this page as a quick lookup of paths and purpose.
- Use [`../how-to/README.md`](../how-to/README.md) for procedural tasks.
- Use [`mcp.md`](mcp.md) for MCP-specific details.

## Core Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Static SPA |
| | | **Auth** |
| GET | `/api/v1/auth/providers` | List enabled OAuth providers |
| GET | `/api/v1/auth/login/{provider}` | Start OAuth login (Google, Microsoft, GitHub) |
| GET | `/api/v1/auth/callback/{provider}` | OAuth callback |
| POST | `/api/v1/auth/logout` | End session |
| | | **Me** |
| GET | `/api/v1/me` | Get profile (`?identity_only=true` for session check) |
| PUT | `/api/v1/me` | Update profile |
| POST | `/api/v1/me/import-apply` | Apply import preview |
| DELETE | `/api/v1/me` | Delete account (GDPR Art. 17) |
| GET | `/api/v1/me/export` | GDPR data export (Art. 15/20) |
| POST | `/api/v1/me/cv-import` | CV import (Docling + LLM) |
| GET | `/api/v1/me/notifications` | List notifications |
| GET/PUT | `/api/v1/me/skills` | Skill self-assessments |
| GET | `/api/v1/me/consents` | List consents |
| POST/DELETE | `/api/v1/me/consents/{scope}` | Grant/revoke consent |
| GET | `/api/v1/me/activity` | Activity feed |
| | | **System** (`can_manage_skills`) |
| GET | `/api/v1/system/statistics` | Admin overview counters and runtime timing stats (LLM, embedding, HNSW) |
| | | **Matches** |
| POST | `/api/v1/matches/prompt` | Natural-language search |
| POST | `/api/v1/matches` | Structured match |
| GET | `/api/v1/matches/me` | Match from own profile embedding |
| GET | `/api/v1/matches/{nodeId}` | Match from a node's embedding |
| | | **Nodes** |
| GET/POST | `/api/v1/nodes` | List / create mesh nodes |
| GET/PUT | `/api/v1/nodes/{nodeId}` | Get / update a node |
| GET | `/api/v1/nodes/{nodeId}/skills` | Node skill assessments |
| GET | `/api/v1/nodes/{nodeId}/profile` | Public profile (read-only) |
| | | **Skills** (`can_manage_skills`) |
| GET/POST | `/api/v1/skills` | List / create catalogs |
| GET/DELETE | `/api/v1/skills/{catalogId}` | Get / delete catalog |
| POST | `/api/v1/skills/{catalogId}/import` | Import definitions (CSV) |
| GET | `/api/v1/skills/{catalogId}/definitions` | List definitions |
| GET | `/api/v1/skills/{catalogId}/categories` | List categories |
| | | **Maintenance** (`X-Maintenance-Key`) |
| POST | `/api/v1/maintenance/ingest/jobs` | ATS job batch upsert |
| POST | `/api/v1/maintenance/purge-consent-tokens` | Remove expired consent tokens |
| POST | `/api/v1/maintenance/enforce-retention` | Hard-delete inactive users past retention |
| POST | `/api/v1/maintenance/run-clustering` | Auto-discover communities (k-means) |
| POST | `/api/v1/maintenance/regenerate-embeddings` | Regenerate node embeddings (`onlyMissing=true` by default; supports `?nodeType=...` and `?onlyMissing=false`) |
| POST | `/api/v1/maintenance/ldap-import/preview` | LDAP import preview |
| POST | `/api/v1/maintenance/ldap-import` | Execute LDAP import |
| | | **MCP** |
| POST | `/mcp` | Streamable HTTP (also `/mcp/sse` for legacy SSE) |
| | | **Ops** |
| GET | `/q/health` | Liveness/readiness probes |
| GET | `/q/metrics` | Micrometer export endpoint (format depends on enabled registry, e.g. Prometheus) |
| GET | `/q/dev-ui` | Quarkus dev dashboard (dev mode only) |

## Notes

- Endpoint authorization depends on OIDC/session state and configured entitlements.
- Maintenance endpoints require `X-Maintenance-Key` and may also enforce IP/CIDR restrictions.
- MCP integration is read-only by design.
- API implementation layering:
  - REST endpoints live under `org.peoplemesh.api.resource`
  - API error contracts and exception mapping live under `org.peoplemesh.api.error`
- `GET /api/v1/system/statistics` includes timing summaries for:
  - `timings.llmInference`
  - `timings.embeddingInference`
  - `timings.hnswSearch`
  Each block provides `sampleCount`, `avgMs`, `p95Ms`, and `maxMs`.
