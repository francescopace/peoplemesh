# Run Maintenance Tasks

Cross-cutting runbook for scheduling, security policy, and CLI-first execution of maintenance tasks.
Task-specific procedures stay in the dedicated how-to guides.

## Audience

- Platform operators
- SRE/DevOps maintainers

## Prerequisites

- Valid maintenance key (`MAINTENANCE_API_KEY` or `PEOPLEMESH_MAINTENANCE_KEY`)
- Network origin allowed by `MAINTENANCE_ALLOWED_CIDRS` (if configured)
- Operational access to scheduler platform (CronJob/EventBridge or equivalent)

## Operational Policy

- Protect all maintenance execution with a valid maintenance key.
- Keep `MAINTENANCE_ALLOWED_CIDRS` restricted to scheduler egress ranges.
- Use idempotent scheduler behavior where possible and define retry/backoff policy.
- Run heavy jobs off-peak and monitor duration/failure trends.

## Task Matrix

| Task | CLI command | HTTP endpoint | Recommended schedule | Procedure section |
|------|-------------|---------------|----------------------|-------------------|
| ATS ingest | `./pmc ats-ingest-jobs --file /path/to/ats-jobs.json` | `POST /api/v1/maintenance/ingest/jobs` | Every 15-60 minutes | [`run-ats-ingest.md#procedure`](../how-to/run-ats-ingest.md#procedure) |
| Purge consent tokens | `./pmc purge-consent-tokens` | `POST /api/v1/maintenance/purge-consent-tokens` | Hourly | [`purge-consent-tokens.md#procedure`](../how-to/purge-consent-tokens.md#procedure) |
| Enforce retention | `./pmc enforce-retention` | `POST /api/v1/maintenance/enforce-retention` | Daily | [`enforce-retention.md#procedure`](../how-to/enforce-retention.md#procedure) |
| Community auto-clustering | `./pmc run-clustering` | `POST /api/v1/maintenance/run-clustering` | Weekly | [`configure-community-auto-clustering.md#procedure`](../how-to/configure-community-auto-clustering.md#procedure) |
| Regenerate embeddings (all, by type, or only missing) | `./pmc regenerate-embeddings ...` | `POST /api/v1/maintenance/regenerate-embeddings` + `GET /api/v1/maintenance/regenerate-embeddings/{jobId}` | On demand / after embedding-model changes or LDAP import | [`regenerate-node-embeddings.md#procedure`](../how-to/regenerate-node-embeddings.md#procedure) |
| LDAP import | `./pmc ldap-import` | `POST /api/v1/maintenance/ldap-import` | Daily | [`configure-ldap-import.md#procedure`](../how-to/configure-ldap-import.md#procedure) |

## Maintenance CLI

Use the CLI script checked into repository root:

- `./pmc --help`

The script requires `bash` and `curl`. For pretty JSON output, it uses `jq` when available (falls back to `python3 -m json.tool`).

Then run commands via executable (omit `--base-url` for local default):

- `./pmc ldap-import`
- `./pmc regenerate-embeddings --node-type USER --only-missing`
- `./pmc regenerate-embeddings-status --job-id <uuid>`
- `./pmc --raw --timeout-seconds 180 run-clustering`

`regenerate-embeddings` defaults to `onlyMissing=true`.
It also defaults to `batchSize=1`, and by default waits for completion through polling.

Defaults:

- `--base-url` defaults to `http://localhost:8080` when omitted
- local `localhost` targets can use dev default key automatically
- non-local targets require `PEOPLEMESH_MAINTENANCE_KEY` (or `MAINTENANCE_API_KEY`)
- timeout defaults to `120` seconds (`PEOPLEMESH_TIMEOUT_SECONDS` / `MAINTENANCE_TIMEOUT_SECONDS`)

Custom remote target example:

- `./pmc run-clustering --base-url https://peoplemesh.example.com --maintenance-key "$MAINTENANCE_API_KEY"`

Equivalent REST APIs remain available for B2B integrations. For endpoint references, see [`../reference/api.md`](../reference/api.md).

## Exportable Bundle

To distribute the CLI without source code:

- Share the `./pmc` file from repository root.

## Verification

- Verify maintenance key rotation policy.
- Restrict source IPs with `MAINTENANCE_ALLOWED_CIDRS`.
- Monitor maintenance task failures and retry strategy.
- Audit maintenance calls in application logs.

## Troubleshooting

- `./pmc: command not found`: run from repository root and ensure the file is executable (`chmod +x ./pmc`).
- `Connection refused`/timeout from CLI: verify the target app is reachable and set `--base-url` explicitly for non-local environments.
- CLI returns `401`/`403`: verify `PEOPLEMESH_MAINTENANCE_KEY`/`MAINTENANCE_API_KEY` and `MAINTENANCE_ALLOWED_CIDRS` rules.
- Repeated scheduler/CLI task failures: inspect application logs and reduce batch size/frequency.
- `./pmc` starts but fails immediately: verify `bash` and `curl` are available on the host.
