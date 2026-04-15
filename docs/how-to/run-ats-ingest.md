# Run ATS Ingest Safely

Import ATS job batches reliably while preserving stable identifiers and scheduling hygiene.

## Why this matters

Safe ingest practices reduce integration risk and support data-governance expectations:

- [GDPR Art. 5(1)(d)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): maintain data accuracy during recurring imports and updates.
- [GDPR Art. 5(1)(f)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): process data with integrity and confidentiality through controlled operations.

## Audience

- Platform operators
- Integration maintainers

## Prerequisites

- Valid `X-Maintenance-Key`
- Source IP permitted by `MAINTENANCE_ALLOWED_CIDRS` (if configured)
- ATS feed payloads containing stable `external_id` values

## Procedure

1. Validate ingest payload shape in a staging environment.
2. Ensure each job has a unique and stable `external_id`.
3. Run ATS ingest via CLI:
   - `./pmc ats-ingest-jobs --file /path/to/ats-jobs.json --base-url http://localhost:8080`
   - The `--file` content is the ATS jobs payload consumed by the ingest process.
4. Keep batch sizes conservative (up to 100 jobs per request supported).
5. Schedule ingest with your external scheduler (for example every 15-60 minutes).

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- Confirm HTTP success responses from ingest calls.
- Validate job nodes appear or update correctly in PeopleMesh.
- Check closure semantics: closed/archived/cancelled/deleted/filled/hired statuses remove jobs.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- Duplicate records: verify ATS `external_id` consistency across runs.
- Unexpected deletions: inspect ATS status mapping in feed payload.
- Throughput issues: lower batch size and increase scheduling frequency.

Note: This guide provides operational implementation guidance and is not legal advice.
