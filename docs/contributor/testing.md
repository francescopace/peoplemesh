# Testing Guide

PeopleMesh uses backend and frontend test suites with coverage enforcement.

## Backend tests

```bash
make test-backend        # unit tests only (Surefire) — no containers
make test-integration    # integration tests only (Failsafe) — starts DevServices containers
mvn verify               # both unit + integration, with JaCoCo coverage check
```

- `make test-backend` / `mvn test`: unit tests via Surefire — Mockito-based, no Quarkus context, no containers
- `make test-integration` / `mvn failsafe:integration-test failsafe:verify`: integration tests via Failsafe — `@QuarkusTest` with DevServices (Postgres/pgvector, Keycloak)
- `mvn verify`: full run (unit + integration) with JaCoCo line coverage gate

Coverage policy:

- JaCoCo enforces line coverage (configured with `jacoco:check` in `pom.xml`)
- Current enforced floor: 80%
- Coverage is merged across unit and integration runs
- The coverage check is skipped when running integration tests alone (integration target runs Failsafe goals directly)
- Exclusions should stay minimal and justified

Test style:

- Unit tests (`*Test.java`): JUnit 5 + Mockito — preferred for service logic
- When extracting collaborator classes from large services, add focused unit tests for the new collaborator in the same PR (do not rely only on higher-level service tests)
- Integration tests (`*IT.java`): `@QuarkusTest` with full API flows against DevServices (Postgres/pgvector, Keycloak)
- Docling and Ollama DevServices are disabled in tests; those services are mocked
- For repository native queries, test both parameter binding and typed projection mapping; keep positional row parsing tests in repository layer only.
- Service tests should mock repository typed projections (not `Object[]`) once a repository contract is typed.

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
- UI tests use direct DOM assertions/events (no framework-specific test harness required)
- Coverage focus: utilities, router, API client, auth, UI components, views
- Coverage thresholds are enforced in `vitest.config.js`
- Prefer adding tests near the feature area (`services`, `utils`, `views`) and run only the impacted suite while iterating.
- Keep regression coverage for shared matching/search behavior and core service orchestration.

## Python tools

Helper scripts under `tools/data/` use only Python standard library modules.
Run them directly from the repository root.

### Unified quality+tuning (`tools/data/mymesh_quality_tuning.py`)

Single quality run:

```bash
python tools/data/mymesh_quality_tuning.py quality \
  --api-url http://localhost:8080 \
  --maintenance-key dev-local-maintenance-key \
  --seed 42 \
  --relevance-mode balanced_affinity
```

Quality run against backend-native defaults (omit `search_options` entirely):

```bash
python tools/data/mymesh_quality_tuning.py quality \
  --api-url http://localhost:8080 \
  --maintenance-key dev-local-maintenance-key \
  --seed 42 \
  --relevance-mode balanced_affinity \
  --omit-search-options
```

Tuning sweep:

```bash
python tools/data/mymesh_quality_tuning.py tune \
  --api-url http://localhost:8080 \
  --maintenance-key dev-local-maintenance-key \
  --seed 42 \
  --relevance-mode balanced_affinity
```

What it does:
- injects candidate overrides into maintenance tuning request `search_options`
- evaluates My Mesh tuning using maintenance key (no admin entitlement required)
- uses eligible real users only (users must return non-empty tuning results)
- defaults to `All Countries` requests to mirror the My Mesh web view; use `--use-seed-country` to restore seed-country filtering
- searches a configurable option space with exhaustive evaluation when the space is small, otherwise random exploration plus neighbor refinement
- writes per-candidate JSON/log plus final `summary.json` and `summary.md` under `tools/data/results/...`

Useful options:
- `--api-sample-size`: number of seed users used for scoring (default 20)
- `--seed-probe-limit`: max users probed to find eligible seeds
- `--min-eligible-seeds`: fail fast if too few eligible seeds are available
- `--seed-user-ids`: force a fixed comma-separated seed set for A/B reproducibility
- `--use-seed-country`: filter requests to the seed user's country instead of the default `All Countries`
- `--omit-search-options`: use backend defaults by omitting `search_options` from the maintenance tuning payload
- `--max-evals`: total number of candidate configurations to evaluate
- `--refine-top-k`: number of best candidates used for neighbor refinement
- `--search-space-json`: override the default discrete search space for weights/profile knobs

### Regression gate on selected baseline

```bash
python tools/data/mymesh_quality_tuning.py gate \
  --summary-json tools/data/results/<run>/summary.json \
  --min-ndcg-at-10 0.85 \
  --min-p10 0.80 \
  --min-mrr 0.80
```

One-shot tune + gate:

```bash
python tools/data/mymesh_quality_tuning.py tune-and-gate \
  --api-url http://localhost:8080 \
  --maintenance-key dev-local-maintenance-key \
  --seed 42 \
  --relevance-mode balanced_affinity
```

Minimal workflow (reproducible):
1. Refresh dev seeds and consents:
   - `python tools/data/create_fake_data.py --workspace /path/to/repo`
2. Recreate local DB:
   - `make clean start`
3. Run tune with fixed seed:
   - `python tools/data/mymesh_quality_tuning.py tune --seed 42`

Notes:
- The tuning endpoint used is `/api/v1/maintenance/tuning/matches/{userId}`.
- Seed users must have active `professional_matching` consent, otherwise runs are non-informative.

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
make clean             # remove build artifacts
```
