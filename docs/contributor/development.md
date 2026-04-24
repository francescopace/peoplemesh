# Development Guide

This guide is for contributors working on PeopleMesh locally.

## Project structure

```
src/main/java/org/peoplemesh/
├── api/
│   ├── error/      API error payload and exception mapping
│   └── resource/   REST resources (JAX-RS endpoints)
├── config/         Application and security configuration
├── domain/
│   ├── dto/        Data transfer objects
│   ├── enums/
│   └── model/      JPA entities and value objects
├── mcp/            MCP tool definitions
├── repository/     Persistence boundary (EntityManager, JPQL, native SQL)
├── security/       HTTP security and OIDC integration
├── service/        Core business logic
└── util/           Shared technical utilities

src/test/java/org/peoplemesh/
├── api/
│   ├── resource/   Resource tests
│   └── ...         API filter/error tests
├── domain/dto/     DTO record tests
├── integration/    End-to-end flows
├── security/       Auth and provider tests
├── service/        Service unit tests
└── util/           Utility tests

src/main/web/
├── index.html      SPA entry point
├── config.js       Runtime config
├── package.json    Frontend dependencies
├── vitest.config.js
├── __tests__/      Frontend unit tests
└── assets/
    ├── css/
    └── js/          Vanilla ES-module frontend (views/components/services/api/utils)
```

## Local development loop

1. Start backend with `mvn quarkus:dev`
2. Run backend tests with `mvn test`
3. Run full verification with `mvn verify`
4. Run frontend tests from `src/main/web` with `npm test`

## Contribution notes

- Keep changes small and focused by topic.
- Add or update tests for behavioral changes.
- Keep layers separated by responsibility and dependency direction:
  - API flow: `api/resource -> service -> repository`
  - MCP flow: `mcp -> service -> repository` (no direct repository access from MCP handlers)
  - `api/resource` handles HTTP boundary only (request/response, status, validation)
  - `service` contains business logic
  - `repository` handles persistence and query logic
- For native/tuple query results, prefer repository-scoped typed projection records over exposing `Object[]` to services.
- Avoid introducing `application` layer classes unless explicitly requested for a specific use case.
- Do not access repositories directly from `api` or `mcp` entrypoints.
- Do not import API-layer types/utilities inside `service`.
- Avoid mixing repository and active-record persistence styles in the same service flow.
- Avoid cosmetic DTOs and trivial dedicated mappers; see architecture policy for details.
- Keep API errors centralized in `api/error`.
- Follow the full architecture guardrails in [`../internals/architecture.md`](../internals/architecture.md).
- For AI-assisted refactors, use:
  - Frontend baseline prompt: [`../../tools/prompts/refactor-frontend-prompt.md`](../../tools/prompts/refactor-frontend-prompt.md)
  - Backend baseline prompt: [`../../tools/prompts/refactor-backend-prompt.md`](../../tools/prompts/refactor-backend-prompt.md)
- For licensing and contribution rules, review [`../../LICENSE`](../../LICENSE) and [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md).

## AI-assisted refactor workflow

1. Choose one bounded feature slice and avoid broad rewrites.
2. Start from the matching baseline prompt and adapt scope to touched files.
3. Preserve external contracts (routes, API payload shape, and behavior) unless explicitly requested.
4. Keep architecture boundaries intact:
   - Frontend flow: `views/components -> services -> api`
   - Backend flow: `api/resource -> service -> repository` and `mcp -> service -> repository`
5. Run relevant tests before and after changes.
6. Publish outcomes with this checklist:
   - Key problems found (by impact)
   - Files changed and rationale
   - Tests executed
   - Residual risks and trade-offs
