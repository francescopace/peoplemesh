# Documentation Style Guide

This guide defines how to write and maintain project documentation in a consistent way.

## Audience

- Maintainers
- Contributors writing or updating docs

## Documentation architecture

Use the current docs structure and keep content in the right category:

- `getting-started/`: onboarding and first run
- `how-to/`: task-oriented procedures
- `reference/`: authoritative lookup material (APIs, config, contracts)
- `internals/`: concepts, rationale, and trade-offs
- `operations/`: runbooks and production procedures
- `contributor/`: contributor and maintainer guidance

`README.md` in repository root must remain short and onboarding-focused.

## Page template rules

### Required sections by document type

- **How-to** pages must include:
  - Why this matters (security/privacy impact for the operation; include relevant regulatory references when applicable)
  - Audience
  - Prerequisites
  - Procedure
  - Verification
  - Troubleshooting
- **Reference** pages must include:
  - Audience
  - How to Use This Page
  - Stable, scannable sections by topic
- **Explanation** pages should include:
  - Audience
  - How to Use This Page
  - Key concepts and trade-offs
- **Operations** pages must include:
  - Audience
  - Prerequisites
  - Procedure
  - Verification
  - Troubleshooting

## Writing style

- Use concise, direct, task-first wording.
- Prefer active voice and imperative verbs in procedures.
- Keep paragraphs short; use bullets for scanability.
- Avoid internal implementation detail unless required for operation or integration.
- Avoid marketing language in technical docs.

## Naming conventions

- Use lowercase, hyphen-separated filenames (for example: `configure-oidc.md`).
- Use action-oriented names for how-to pages (`configure-*`, `run-*`, `enable-*`).
- Use noun-oriented names for reference pages (`api.md`, `configuration.md`).

## Command and code formatting

- Use fenced code blocks for commands and payload examples.
- Keep examples minimal and copy-paste ready.
- Prefer explicit placeholders for secrets and identifiers.
- Do not include real credentials or production tokens.

## Link and navigation rules

- Every new page must be linked from `docs/README.md`.
- New how-to pages must be linked from `docs/how-to/README.md`.
- Use relative links within `docs/`.
- Update cross-references when moving or splitting content.

## Content boundaries

- Keep `README.md` at high level:
  - project purpose
  - quick start
  - production essentials
  - links to docs
- Move deep technical content into `docs/`.
- Keep legal and governance docs at repository root unless explicitly reorganized.

## Maintenance rules

- Treat documentation changes as part of feature completion.
- Update docs in the same pull request as behavior/config changes.
- Remove stale references immediately when endpoints, variables, or flows change.

## Documentation PR checklist

Before merging doc changes, verify:

- Section templates are respected for the document type
- Links resolve and point to existing files
- Commands are accurate and executable
- Configuration keys and endpoint paths match current implementation
- README remains concise and does not duplicate deep reference material
