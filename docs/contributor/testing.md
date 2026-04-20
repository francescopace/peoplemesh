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
- `mvn verify`: full run (unit + integration) with JaCoCo line coverage gate

Coverage policy:

- JaCoCo enforces line coverage (configured with `jacoco:check` in `pom.xml`)
- Current enforced floor: 80%
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
make test-frontend 
# run a single suite
npm test -- __tests__/path/to/suite.test.js
```

- Test runner: Vitest
- Environment: jsdom
- Coverage focus: utilities, router, API client, auth, UI components, views
- Coverage thresholds are enforced in `vitest.config.js`
- Prefer adding tests near the feature area (`services`, `utils`, `views`) and run only the impacted suite while iterating.
- Keep regression coverage for shared matching/search behavior and core service orchestration.

## Python tools environment

Some helper scripts under `tools/` require a local virtual environment.

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

Quick run:

```bash
python tools/eval_search_quality.py
python tools/eval_search_quality.py --api-sample-size 50 --seed 42
```

- Runs direct embedding and API (`POST /api/v1/matches`) evaluation and prints the global quality gate.
- Use `--help` for all available flags.
- For full methodology and decision criteria, see `docs/contributor/embedding-evaluation.md`.

## Suggested CI order

1. `make test-backend` — fast unit tests, no containers
2. `make test-integration` — integration tests with DevServices
3. `make test-frontend` — frontend tests
4. `mvn verify` — full run with coverage gate (or run steps 1+2 in CI and verify separately)

Fail fast on any regression before merge.

## Coverage checklist for PRs

- New business logic ships with tests in the same PR
- No unexplained drop in backend JaCoCo line coverage
- No unexplained drop in frontend Vitest line coverage
- If coverage regresses, add tests before raising thresholds again

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
