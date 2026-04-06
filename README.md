# People Mesh - AI does not take your job. It helps you find the right one.

AI-powered recruiting and job discovery via MCP server. Help companies find qualified candidates and help people discover relevant job opportunities using AI-generated professional profiles.

> Website: [peoplemesh.org](https://peoplemesh.org)

> **Status:** MVP — under active development, not production-ready.

## Project Context

PeopleMesh is a privacy-first backend for AI-powered recruiting, built for a two-sided marketplace:
- companies searching for suitable profiles to hire
- candidates searching for relevant roles and opportunities

The platform is powered by AI agents and MCP tools.
Instead of asking users to manually fill long profiles, an AI assistant (like Claude, ChatGPT or Gemini) can build a structured profile draft from prior conversations (using memory) and submit it through MCP tools only after user review.

The goal is to improve hiring quality and speed while minimizing personal data exposure and giving users explicit control over consent and sharing.

## What The Project Does

- Receives structured professional profiles from MCP clients
- Generates and stores semantic embeddings for profile matching
- Finds relevant profile and job matches using composite scoring (vector similarity + metadata)
- Supports native job posting and role lifecycle for recruiters
- Provides recruiter inbox, shortlist, and candidate pipeline workflows per job
- Manages mediated contact workflows between candidates and companies/recruiters
- Exposes GDPR workflows (export, deletion, restriction) and privacy activity
- Applies security controls like encryption, rate limiting, and audit logging

Current scope includes profile-centric discovery, job-centric matching, and recruiter pipeline operations.

## High-Level Flow

### Candidate

1. Authenticates via OAuth2/OIDC (Google, GitHub, or Microsoft).
2. An AI assistant prepares a structured profile draft and asks for confirmation.
3. MCP client sends the approved profile with a consent token via `peoplemesh_submit_profile`.
4. PeopleMesh stores data securely, computes embeddings, and makes the profile searchable.
5. Candidate receives relevant match suggestions and can initiate or accept mediated connections.
6. Candidate can export or erase their data at any time (GDPR).

### Company / Recruiter

1. Authenticates via OAuth2/OIDC.
2. Searches for candidates using `peoplemesh_find_matches` with optional filters (skills, work mode, location...).
3. Reviews ranked results with composite scores (semantic similarity + metadata).
4. Sends mediated connection requests to selected candidates.
5. If the candidate accepts, shared contact info is exchanged.

## Architecture

```
┌──────────────┐         ┌──────────────────────────────────────┐
│  MCP Client  │         │           PeopleMesh (Quarkus)        │
│ (Claude, GPT,│  MCP/   │                                      │
│  Gemini ...) │────────►│  MCP Tools ──► Services ──► Domain   │
└──────────────┘  HTTP   │       │            │           │      │
                         │       ▼            ▼           ▼      │
                         │  ┌────────┐  ┌──────────┐  ┌───────┐ │
                         │  │ Redis  │  │ Postgres │  │ Vault │ │
                         │  │  rate  │  │ pgvector │  │transit│ │
                         │  │ limits │  │ profiles │  │encrypt│ │
                         │  │consent │  │embedding │  │       │ │
                         │  └────────┘  └──────────┘  └───────┘ │
                         └──────────────────────────────────────┘
```

- **Quarkus 3.33.1 LTS** (Java 21) with MCP Server HTTP extension 1.11.0
- **PostgreSQL 16 + pgvector** for persistence and semantic matching
- **Redis 7** for rate limiting and consent token management
- **HashiCorp Vault** for field-level encryption (Transit engine)
- **Quarkus OIDC** for OAuth2 authentication (Google, GitHub, Microsoft)
- **Flyway** for schema migrations
- **SmallRye Health** for readiness/liveness probes

## MCP Tools

All tools require authentication (bearer token) in production. In dev mode, auth is disabled for convenience.

| Tool | Parameters | Description |
|------|------------|-------------|
| `peoplemesh_submit_profile` | `profileJson` (String), `consentToken` (String) | Create or replace the authenticated user's profile from an AI-generated JSON payload |
| `peoplemesh_get_my_profile` | — | Return the authenticated user's profile |
| `peoplemesh_update_my_profile` | `profileJson` (String) | Apply partial updates to the authenticated user's profile |
| `peoplemesh_delete_my_profile` | — | Soft-delete profile and trigger data lifecycle workflows (~30-day permanent removal) |
| `peoplemesh_find_matches` | `filtersJson` (String, optional) | Return ranked profile matches using semantic + metadata scoring, with optional filters |
| `peoplemesh_create_job` | `jobJson` (String) | Create a new recruiter job posting in `DRAFT` status |
| `peoplemesh_list_my_jobs` | — | List jobs owned by the authenticated recruiter |
| `peoplemesh_update_job` | `jobId` (String), `jobJson` (String) | Update recruiter job fields and refresh job embedding |
| `peoplemesh_transition_job_status` | `jobId` (String), `status` (String) | Move a job through lifecycle states (`DRAFT`, `PUBLISHED`, `PAUSED`, `FILLED`, `CLOSED`) |
| `peoplemesh_find_job_matches` | `filtersJson` (String, optional) | Return ranked job matches for the authenticated candidate |
| `peoplemesh_find_candidates_for_job` | `jobId` (String), `filtersJson` (String, optional) | Return ranked candidate matches for a recruiter-owned job |
| `peoplemesh_add_candidate_to_pipeline` | `jobId` (String), `targetProfileId` (String), `updateJson` (String, optional) | Add or upsert a candidate in recruiter pipeline/shortlist |
| `peoplemesh_update_pipeline_candidate` | `jobId` (String), `candidateUserId` (String), `updateJson` (String) | Update stage, shortlist flag, and notes for a pipeline entry |
| `peoplemesh_get_job_pipeline` | `jobId` (String), `stage` (String, optional), `shortlistedOnly` (boolean) | List candidates in pipeline for a specific job |
| `peoplemesh_get_job_inbox` | `jobId` (String) | List inbox candidates (`APPLIED`) for a specific job |
| `peoplemesh_request_connection` | `targetProfileId` (String), `message` (String, optional, max 300 chars) | Send a mediated connection request to another profile |
| `peoplemesh_respond_to_connection` | `requestId` (String), `accept` (boolean) | Accept or reject a pending connection request |
| `peoplemesh_get_connections` | — | List active connections and pending requests for the authenticated user |

### Example: Submit a Profile

`profileJson` payload:

```json
{
  "profile_version": "1.0",
  "generated_at": "2026-04-06T12:00:00Z",
  "consent": {
    "explicit": true,
    "timestamp": "2026-04-06T11:55:00Z",
    "scope": ["profile_storage", "matching"],
    "retention_days": 365,
    "revocable": true
  },
  "professional": {
    "roles": ["Software Engineer"],
    "seniority": "SENIOR",
    "industries": ["Technology"],
    "skills_technical": ["Java", "PostgreSQL"],
    "skills_soft": ["Communication"],
    "tools_and_tech": ["Quarkus", "Docker"],
    "languages_spoken": ["English"],
    "work_mode_preference": "REMOTE",
    "employment_type": "OPEN_TO_OFFERS"
  },
  "interests_professional": {
    "topics_frequent": ["distributed systems"],
    "learning_areas": ["ML ops"],
    "project_types": ["open source"],
    "collaboration_goals": ["NETWORKING", "KNOWLEDGE_EXCHANGE"]
  },
  "geography": {
    "country": "IT",
    "city": "Rome",
    "timezone": "Europe/Rome"
  },
  "privacy": {
    "show_city": false,
    "show_country": true,
    "searchable": true,
    "contact_via": "mcp_connection_only"
  }
}
```

### Example: Find Matches with Filters

`filtersJson` payload (all fields optional):

```json
{
  "skillsTechnical": ["Java", "Rust"],
  "collaborationGoals": ["MENTORSHIP"],
  "workMode": "HYBRID",
  "employmentType": "FREELANCE",
  "country": "DE"
}
```

### Enum Reference

| Enum | Values |
|------|--------|
| Seniority | `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `EXECUTIVE` |
| WorkMode | `REMOTE`, `HYBRID`, `OFFICE`, `FLEXIBLE` |
| EmploymentType | `EMPLOYED`, `FREELANCE`, `FOUNDER`, `LOOKING`, `OPEN_TO_OFFERS` |
| CollaborationGoal | `MENTORSHIP`, `COFOUNDER`, `FREELANCE_PROJECT`, `JOB`, `NETWORKING`, `KNOWLEDGE_EXCHANGE` |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### Run in Dev Mode

```bash
# Starts Postgres (pgvector), Redis, and Vault automatically via DevServices
mvn quarkus:dev
```

In dev mode:
- Authentication is **disabled** — all MCP and API endpoints are open
- Embedding provider defaults to **`noop`** (no external API needed)
- DevServices automatically provisions Postgres, Redis, and Vault containers

MCP endpoints:

- `http://localhost:8080/mcp` (recommended: Streamable HTTP)
- `http://localhost:8080/mcp/sse` (legacy compatibility: HTTP + SSE)

Verify it works:

```bash
# Health check
curl -s http://localhost:8080/q/health | jq .

# Quarkus Dev UI (browser)
open http://localhost:8080/q/dev-ui
```

### Run with Docker Compose

```bash
cp .env.example .env
# Edit .env with your secrets
mvn package -DskipTests
docker compose up -d --build
```

If you changed `docker-compose.yml` healthchecks, recreate services:

```bash
docker compose up -d --build --force-recreate
```

## Configuration

Configuration is managed via environment variables (see `.env.example`).

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_USER` | No | `peoplemesh` | PostgreSQL username |
| `POSTGRES_PASSWORD` | Yes (prod) | `changeme` | PostgreSQL password |
| `POSTGRES_DB` | No | `peoplemesh` | PostgreSQL database name |
| `REDIS_PASSWORD` | Yes (prod) | `changeme` | Redis password |
| `VAULT_TOKEN` | Yes (prod) | `dev-root-token` | Vault root/access token |
| `VAULT_ADDR` | No | `http://vault:8200` | Vault server address |
| `EMBEDDING_PROVIDER` | No | `noop` | Embedding backend: `openai`, `cohere`, or `noop` |
| `OPENAI_API_KEY` | If provider=openai | — | OpenAI API key |
| `COHERE_API_KEY` | If provider=cohere | — | Cohere API key |
| `OIDC_GOOGLE_CLIENT_ID` | If using Google | — | Google OAuth2 client ID |
| `OIDC_GOOGLE_CLIENT_SECRET` | If using Google | — | Google OAuth2 client secret |
| `OIDC_GITHUB_CLIENT_ID` | If using GitHub | — | GitHub OAuth2 client ID |
| `OIDC_GITHUB_CLIENT_SECRET` | If using GitHub | — | GitHub OAuth2 client secret |
| `OIDC_MICROSOFT_CLIENT_ID` | If using Microsoft | — | Microsoft OAuth2 client ID |
| `OIDC_MICROSOFT_CLIENT_SECRET` | If using Microsoft | — | Microsoft OAuth2 client secret |
| `CONSENT_TOKEN_SECRET` | Yes (prod) | — | HMAC-SHA256 signing key (base64-encoded, min 32 bytes) |

> In dev mode (`mvn quarkus:dev`), OIDC, database, Redis, and Vault are auto-configured by DevServices. No environment variables are needed.

## Development

### Project Structure

```
src/main/java/org/peoplemesh/
├── api/            REST resources (AuthResource, MeResource, MatchResource, JobResource...)
├── config/         Application and security configuration
├── domain/
│   ├── dto/        Data transfer objects (ProfileSchema, MatchResult, JobMatchResult...)
│   ├── enums/      Seniority, WorkMode, EmploymentType, CollaborationGoal, JobStatus...
│   └── model/      JPA entities
├── matching/       Embedding providers (OpenAI, Cohere, NoOp)
├── mcp/            MCP tool definitions (ProfileTools, MatchingTools, JobTools, RecruiterTools...)
├── scheduling/     Scheduled jobs (RetentionJob, BackupPurgeJob, ProfileDecayJob)
├── security/       Filters (RateLimitFilter, SecurityHeadersFilter)
└── service/        Core business logic (profiles, jobs, matching, pipeline, GDPR, consent, audit)
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for DevServices)
mvn verify
```

### Database Migrations

Schema changes use Flyway and live under `src/main/resources/db/migration/`. Migrations run automatically on startup.

## Product Roadmap

### Now (Implemented)
- Candidate profile ingestion through MCP tools
- Semantic profile matching and ranked results
- Native job posting and role lifecycle management
- Candidate-to-job and recruiter-to-candidate matching
- Recruiter inbox, shortlists, and interview pipeline basics
- Explainability breakdown in match scores (components + reason codes)
- Mediated connections between candidates and companies/recruiters
- Privacy dashboard with GDPR export, deletion, and restriction workflows

### Next
- LinkedIn OAuth as additional OIDC provider for stronger social proof
- Employer email domain verification flag (`employer_email_verified`) based on known corporate domains
- Composite trust score on profiles (OIDC provider, email domain, profile completeness, account age)
- Trust score as optional filter in `find_matches` and `find_candidates_for_job`
- ATS and HRIS integrations
- Team collaboration for shared recruiter pipelines
- Advanced explainability and recommendation narratives
- Enterprise permission model for recruiter teams

### Later
- KYC for recruiters and companies via document verification (e.g. Stripe Identity or Persona)
- Verifiable Credentials (W3C standard) for cryptographically signed professional credentials
- Enterprise controls (SSO/SCIM, advanced audit, data residency options)
- Marketplace intelligence and benchmarking insights

## Business Model

PeopleMesh follows a two-sided model:

- Free access for individuals/candidates in the MVP phase
- Paid plans for companies and recruiting teams (after MVP)
- Privacy and consent controls are product fundamentals, not premium add-ons

Monetization direction (without selling personal data):

- B2B subscriptions for hiring teams (talent search seats, pipelines, collaboration)
- Job distribution and matching workflow features (advanced filters, automation, integrations)
- Enterprise offerings (compliance controls, private deployment options, support SLAs)

## Links

- [Model Context Protocol (MCP) Specification](https://modelcontextprotocol.io/)
- [Quarkus MCP Server Extension](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html)
- [Quarkus Framework](https://quarkus.io/)
- [pgvector — Vector Similarity Search for PostgreSQL](https://github.com/pgvector/pgvector)

## License

Proprietary — All rights reserved.
