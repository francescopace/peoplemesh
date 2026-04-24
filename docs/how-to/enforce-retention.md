# Enforce Data Retention

Apply retention rules to remove inactive records that exceeded policy thresholds.

## Why this matters

Retention enforcement supports GDPR storage limitation and accountability practices:

- [GDPR Art. 5(1)(e)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): personal data should not be kept longer than necessary.
- [GDPR Art. 5(2)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): controllers should be able to demonstrate compliance.

## Audience

- Platform operators
- Privacy and compliance maintainers

## Prerequisites

- Maintenance API key (`MAINTENANCE_API_KEY`)
- Source IP allowed by `MAINTENANCE_ALLOWED_CIDRS` (if configured)
- Retention policy configured and approved by compliance owners
- Access to maintenance CLI executable (`pmc`)

## Procedure

1. Confirm retention policy window and target scope before execution.
2. Run retention enforcement via CLI:
   - `./pmc enforce-retention --base-url http://localhost:8080`
3. Review deletion summary from command output and application logs.
4. Schedule recurring retention runs (typically daily).

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- Command exits successfully without runtime errors.
- Application logs show a retention enforcement summary.
- Inactive users past retention are removed as expected.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- Too many deletions: verify retention configuration and environment target before rerun.
- No records removed: verify retention thresholds and candidate inactivity data.

Note: This guide provides operational implementation guidance and is not legal advice.
