# Contributing to PeopleMesh

Thanks for contributing.
This guide summarizes the expected workflow for pull requests.

## Before you start

- Read [`README.md`](README.md)
- Review technical docs in [`docs/README.md`](docs/README.md)
- Review project license in [`LICENSE`](LICENSE)

## Development workflow

1. Fork and create a focused branch
2. Implement the change with tests
3. Run local checks:
   - `mvn test`
   - `mvn verify`
   - frontend tests if needed (`cd src/main/web && npm test`)
4. Open a pull request with:
   - clear problem statement
   - scope of change
   - test evidence

## Coding and review expectations

- Prefer small, reviewable changes over large mixed commits
- Keep behavior changes covered by tests
- Preserve backward compatibility when possible
- Document user-visible or operator-visible changes

## Logging and comments conventions

- Use `LOG.infof/debugf/warnf/errorf` when interpolating values.
- When logging an exception, prefer `LOG.errorf(e, "...")` or `LOG.warnf(e, "...")`.
- Keep log messages short, searchable, and in English.
- Prefer comments that explain *why* (not *what*), and keep them concise.
- Use a single section-comment style in Java code: `// === Section name ===`.

## Legal

PeopleMesh uses the Developer Certificate of Origin (DCO).
By contributing, you certify that you have the right to submit your changes under the project license.

Add a sign-off to every commit:

- `git commit -s -m "Your commit message"`

Each commit must include a `Signed-off-by:` trailer matching the commit author identity.
