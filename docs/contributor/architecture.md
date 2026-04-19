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

## Frontend Layering (Web App)

For `src/main/web`, follow a lightweight layered flow:

- `views/routes -> services -> api`.
- `views` compose UI and route-level orchestration only.
- Reusable UI feature blocks should live in `components` (for example profile sections or catalog panels), so views do not import other views.
- `services` hold feature/business logic and data orchestration.
- `api` is the only layer that performs HTTP calls.
- `utils` stay stateless (format/parsing/date/helpers), not business orchestration.

Dependency direction rules:

- Views/components must not import API clients directly.
- Services may import `api` and `utils`.
- Utilities must not import views, services, or API modules.
- API modules must not import views/components/services.

## DTO and Mapper Rules

- Avoid cosmetic DTOs (1:1 copies without behavior or boundary value).
- DTOs are allowed only for contracts, validation, redaction, derived views, or versioning.
- Prefer local mapping in services for simple conversions.
- Introduce dedicated mapper classes only for non-trivial, reused mapping logic.

## Persistence Consistency

- Keep persistence strategy consistent per feature.
- Avoid mixing repository usage with direct active-record access in the same service flow.
