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
└── service/        Core business logic

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
    └── js/
```

## Local development loop

1. Start backend with `mvn quarkus:dev`
2. Run backend tests with `mvn test`
3. Run full verification with `mvn verify`
4. Run frontend tests from `src/main/web` with `npm test`

## Contribution notes

- Keep changes small and focused by topic.
- Add or update tests for behavioral changes.
- Keep layers separated by responsibility:
  - `api/resource` handles HTTP request/response only.
  - `service` contains business/use-case orchestration.
  - `repository` handles all persistence and query logic.
- Do not access `EntityManager`, Panache static queries, or native SQL from `service`, `api`, or `mcp` packages.
- Keep API errors centralized in `api/error`.
- For licensing details, review [`../../CLA.md`](../../CLA.md).
