# Architecture Guidelines

This repository follows a pragmatic layered architecture aimed at simplicity.

## Default Flow

- Api flow: `api/resource -> service -> repository`.
- MCP flow: `mcp -> service -> repository`.

## Package Boundaries

- `api` and `mcp`: transport entrypoints and protocol concerns.
- `service`: business logic.
- `repository`: persistence access.
- `domain`: entities, enums, exceptions, and DTO contracts.

## Dependency Rules

- API entrypoints do not call repositories directly.
- Services do not import API-layer utilities/types.
- MCP tools do not depend directly on repositories.
- Repositories do not depend on upper layers.

## DTO and Mapper Rules

- Avoid cosmetic DTOs (1:1 copies without behavior or boundary value).
- DTOs are allowed only for contracts, validation, redaction, derived views, or versioning.
- Prefer local mapping in services for simple conversions.
- Introduce dedicated mapper classes only for non-trivial, reused mapping logic.

## Persistence Consistency

- Keep persistence strategy consistent per feature.
- Avoid mixing repository usage with direct active-record access in the same service flow.
