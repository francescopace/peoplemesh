Refactor this Quarkus repository using a pragmatic, incremental approach that improves maintainability without over-engineering.

Architecture direction (mandatory):
- Use a layered + clean-lite approach (not pure hexagonal).
- Preferred flow: api -> service -> repository (REST) and mcp -> service -> repository (MCP).
- Do not introduce new application layer classes unless explicitly requested.
- Keep top-level packages stable: api, service, repository, domain, mcp, util.
- Keep package structure shallow; avoid creating many nested folders.
- Keep util for shared stateless helpers (formatting/parsing/building), not business orchestration.
- Move stateless helper classes currently parked in service to util where appropriate.

Core objectives:
1) Readability and cleanup
- Improve clarity, consistency, naming, and remove real duplication.
- Split overly long/complex methods only when it improves comprehension.
- Flag service classes exceeding ~500 lines; split only when cohesion is genuinely low, not for line-count alone.

2) Responsibility boundaries
- API resources handle HTTP boundary concerns only.
- Business logic lives in service.
- Persistence logic lives in repository.
- Prefer repository-mediated access; do not mix repository and direct active-record calls in the same service.
- Consolidate Panache entity static helpers into the corresponding repository when they duplicate repository queries.
- Keep transactional decisions in service layers, not in API endpoints.
- Use @ApplicationScoped consistently for services and repositories; do not change CDI scope without justification.
- The project uses RESTEasy Reactive: respect existing @Blocking annotations and do not remove them without verifying the call chain is non-blocking.
- Enforce dependency direction:
  - service must not depend on api package/types
  - mcp entrypoints must not directly use repositories
  - repository must not depend on upper layers

3) DTO/mapper policy (mandatory)
- No cosmetic DTOs (no 1:1 clones of model/entity without value).
- DTOs are allowed only when needed for:
  - different API contract
  - boundary validation
  - sensitive data redaction
  - derived/aggregated fields
  - payload versioning/backward compatibility
- Avoid dedicated mapper classes for trivial mapping.
- Use dedicated mappers only when mapping is non-trivial and reused.

4) Logging and security
- Use org.jboss.logging.Logger with meaningful level usage.
- Do not log secrets or sensitive values.
- Validate input at API boundary (Jakarta Validation).
- Keep query code parameterized and avoid leaking internal details in error responses.

5) Performance and scope
- Reduce unnecessary DB/external calls and over-fetching.
- Do not introduce new cache layers.
- Keep refactor incremental; do not rewrite the whole codebase.
- Preserve behavior and external API contracts unless explicitly requested.

6) Testing and output
- Update/add tests where behavior changes.
- Prefer Mockito-based unit tests for service logic; reserve @QuarkusTest + DevServices for integration tests only.
- Do not introduce explicit Testcontainers dependencies; integration tests rely on Quarkus DevServices.
- Run relevant test suites and report results.
- Respect coverage gate of 80% line ratio. Do not add new JaCoCo excludes or reduce scanned classes beyond what is already configured.
- Return:
  - key problems found (by impact),
  - files changed and rationale,
  - tests executed,
  - residual risks/trade-offs.
