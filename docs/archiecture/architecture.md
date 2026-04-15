# Architecture

PeopleMesh is built as a Quarkus backend serving both a browser SPA and MCP clients.

## Audience

- Architects and tech leads
- Contributors onboarding to backend design
- Reviewers validating system boundaries

## How to use this page

- Read this page for system-level concepts and component relationships.
- Use [`../reference/api.md`](../reference/api.md) for endpoint contracts.
- Use [`../reference/configuration.md`](../reference/configuration.md) for runtime settings.

## High-level flow

```
┌──────────┐  ┌──────────────┐  ┌────────────┐
│ Browser  │  │  MCP Client  │  │ ATS / LDAP │
│  (SPA)   │  │(Claude, GPT, │  │  (ingest)  │
└────┬─────┘  │ Gemini ...)  │  └─────┬──────┘
     │        └──────┬───────┘        │
     │ HTTPS         │ MCP/HTTP       │ REST
     ▼               ▼                ▼
┌─────────────────────────────────────────────┐
│              PeopleMesh (Quarkus)           │
│                                             │
│  REST API ◄──► Services ◄──► MCP Tools      │
│       │            │                        │
│       │     ┌──────┴────────┐               │
│       │     │  LangChain4j  │               │
│       │     │  (LLM + Emb)  │               │
│       │     └──────┬────────┘               │
│       │            │                        │
│       ▼            ▼                        │
│  ┌──────────────────────────┐               │
│  │  PostgreSQL + pgvector   │               │
│  └──────────────────────────┘               │
└─────────────────────────────────────────────┘
        │                │
        ▼                ▼
┌──────────────┐  ┌───────────────┐
│ OIDC Provs   │  │ LLM Provider  │
│ Google, MSFT,│  │ Ollama (dev)  │
│ GitHub       │  │ OpenAI (prod) │
└──────────────┘  └───────────────┘
```

## Core concepts

- All searchable entities are represented as mesh nodes in a unified model.
- Natural language prompts are transformed into structured constraints and vector queries.
- Matching uses hybrid ranking (semantic similarity plus metadata-driven scoring).
- The same domain services power both REST and MCP read flows.

## Primary technology choices

- Quarkus 3.x and Java 21 for the backend
- PostgreSQL 16 with pgvector for persistence and vector search
- LangChain4j as model-provider abstraction
- OIDC for login and identity federation
- Flyway for schema evolution
- Docling for CV parsing workflows

## Data boundaries and schemas

Main schema responsibilities:

- `mesh`: nodes and consent data
- `identity`: user identities and provider linkage
- `skills`: skill catalogs and assessments
- `audit`: pseudonymized audit events

## Architectural trade-offs

- A unified node model simplifies cross-entity search at the cost of richer per-domain specialization.
- Hybrid scoring improves relevance control but introduces tuning complexity.
- Provider abstraction (Ollama/OpenAI) keeps deployment flexible while requiring careful configuration discipline.

## Additional details

- For endpoint-level behavior, see [`../reference/api.md`](../reference/api.md).
- For config defaults and environment-specific behavior, see [`../reference/configuration.md`](../reference/configuration.md).
