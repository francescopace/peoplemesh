# Testing Guide

PeopleMesh uses backend and frontend test suites with coverage enforcement.

## Backend tests

```bash
mvn test
mvn verify
```

- `mvn test`: unit tests (Surefire)
- `mvn verify`: unit + integration tests (Failsafe) and JaCoCo coverage checks

Coverage policy:

- JaCoCo enforces 80% line coverage (configured with `jacoco:check` includes in `pom.xml`)
- Coverage is merged across unit and integration runs
- Exclusions should stay minimal and justified (`LdapImportService` remains excluded due to infrastructure-bound LDAP dependency)

Test style:

- Unit tests: JUnit 5 + Mockito
- Integration tests: full API flows against DevServices dependencies
- Panache-heavy behavior is primarily covered in integration tests

## Frontend tests

```bash
cd src/main/web
npm install
npm test
npm run test:watch
```

- Test runner: Vitest
- Environment: jsdom
- Coverage focus: utilities, router, API client, auth, UI components, views

## Suggested CI order

1. `mvn test`
2. `mvn verify`
3. frontend tests (`npm test`)

Fail fast on any regression before merge.

## Common Make targets

```bash
make start
make test-backend
make test-frontend
make image
make container
make clean
```
