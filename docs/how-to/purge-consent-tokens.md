# Purge Consent Tokens

Remove expired consent tokens on a recurring schedule to keep data compliant and clean.

## Why this matters

Consent-token cleanup supports privacy and compliance hygiene by reducing stale consent artifacts:

- [GDPR Art. 5(1)(e)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): avoid retaining personal data beyond necessity.
- [GDPR Art. 7(1)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): maintain clear and auditable consent evidence.

## Audience

- Platform operators
- Privacy and compliance maintainers

## Prerequisites

- Maintenance API key (`MAINTENANCE_API_KEY`)
- Source IP allowed by `MAINTENANCE_ALLOWED_CIDRS` (if configured)
- Access to maintenance CLI executable (`pmc`)

## Procedure

1. Schedule a low-impact window (typically hourly).
2. Run consent token cleanup via CLI:
   - `./pmc purge-consent-tokens --base-url http://localhost:8080`
3. Capture command output and logs for audit trail.
4. Add/verify scheduler automation for recurring execution.

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- Command exits successfully without runtime errors.
- Application logs show maintenance execution for consent token purge.
- Expired consent tokens no longer appear in the data store.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- No tokens removed unexpectedly: verify there are expired records to purge.
- Frequent purge failures: inspect database/logging errors and reduce scheduler overlap.

Note: This guide provides operational implementation guidance and is not legal advice.
