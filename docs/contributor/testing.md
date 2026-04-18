# Testing Guide

PeopleMesh uses backend and frontend test suites with coverage enforcement.

## Backend tests

```bash
make test-backend        # unit tests only (Surefire) — no containers
make test-integration    # integration tests only (Failsafe) — starts DevServices containers
mvn verify               # both unit + integration, with JaCoCo coverage check
```

- `make test-backend` / `mvn test`: unit tests via Surefire — Mockito-based, no Quarkus context, no containers
- `make test-integration` / `mvn verify -DskipUnitTests=true`: integration tests via Failsafe — `@QuarkusTest` with DevServices (Postgres/pgvector, Keycloak)
- `mvn verify`: full run (unit + integration) with JaCoCo 80% line coverage gate

Coverage policy:

- JaCoCo enforces 80% line coverage (configured with `jacoco:check` in `pom.xml`)
- Coverage is merged across unit and integration runs
- The coverage check is skipped when running integration tests alone (`-DskipUnitTests=true`)
- Exclusions should stay minimal and justified (`LdapImportService` remains excluded due to infrastructure-bound LDAP dependency)

Test style:

- Unit tests (`*Test.java`): JUnit 5 + Mockito — preferred for service logic
- Integration tests (`*IT.java`): `@QuarkusTest` with full API flows against DevServices (Postgres/pgvector, Keycloak)
- Docling and Ollama DevServices are disabled in tests; those services are mocked

## Frontend tests

```bash
cd src/main/web
npm install
npm test
npm run test:watch
# run a single view suite
npm test -- __tests__/views/search.test.js
# run shared term matching utility tests
npm test -- __tests__/utils/term-matching.test.js
```

- Test runner: Vitest
- Environment: jsdom
- Coverage focus: utilities, router, API client, auth, UI components, views
- Shared skill term matching logic (used by both Search and My Mesh highlighting) is covered in `src/main/web/__tests__/utils/term-matching.test.js`
- Search highlighting regression coverage lives in `src/main/web/__tests__/views/search.test.js`

## Python tools environment

Some helper scripts under `tools/` (for example `tools/eval_search_quality.py`) require
Python packages and should run inside a local virtual environment.

```bash
cd /Users/fpace/git/peoplemesh
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r tools/requirements.txt
```

Run scripts with the venv active, then exit when done:

```bash
python tools/eval_search_quality.py
deactivate
```

### Search quality evaluation (`tools/eval_search_quality.py`)

The search evaluation script now runs both:

- direct embedding retrieval evaluation
- API evaluation on `POST /api/v1/matches` (schema-input), with two relevance views:
  - embedding-neighbors relevance
  - strict profile-overlap relevance

Minimal usage:

```bash
python tools/eval_search_quality.py
```

Common options:

- `--query-suite {default|it-only|it-strict}` to focus query sets
- `--api-sample-size <N>` to control sampled USER seeds for API eval
- `--seed <N>` for deterministic runs
- `--query-prefix <text>` for direct embedding eval ablations

Metric notes:

- `R@20` is classic recall against full relevant set.
- `Rc@20` is capped recall: `found@20 / min(total_relevant, 20)`.
  - Prefer `Rc@20` when strict relevance produces very large relevant pools.

Quality gate output:

- `direct_overall`: summary for direct embedding block
- `api_overall`: summary for API strict block
- `overall_global`: conservative global status (`worst(direct_overall, api_overall)`)
- `Action Hints`:
  - quiet single-line hint when all checks are GREEN
  - detailed guidance only when status is YELLOW/RED

## Suggested CI order

1. `make test-backend` — fast unit tests, no containers
2. `make test-integration` — integration tests with DevServices
3. `make test-frontend` — frontend tests
4. `mvn verify` — full run with coverage gate (or run steps 1+2 in CI and verify separately)

Fail fast on any regression before merge.

## Common Make targets

```bash
make start             # dev mode (quarkus:dev)
make test-backend      # unit tests only (Surefire, no containers)
make test-integration  # integration tests only (Failsafe, DevServices)
make test-frontend     # frontend tests
make image             # build JVM container image
make container         # alias for image
make clean             # remove build artifacts
```
