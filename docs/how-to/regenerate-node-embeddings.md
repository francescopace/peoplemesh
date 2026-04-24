# Regenerate Node Embeddings

Refresh missing or all node embeddings after data/model changes.

## Why this matters

Embedding maintenance helps keep derived data aligned with source updates and model changes:

- [GDPR Art. 5(1)(d)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): support accuracy of derived representations after source-data changes.
- [GDPR Art. 5(1)(f)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): run controlled maintenance to preserve integrity of search and ranking behavior.

## Audience

- Platform operators
- Search and relevance maintainers

## Prerequisites

- Maintenance API key (`MAINTENANCE_API_KEY`)
- Source IP allowed by `MAINTENANCE_ALLOWED_CIDRS` (if configured)
- Access to maintenance CLI executable (`pmc`)

## Procedure

1. Decide scope:
   - all node types
   - one `nodeType` only (for example `USER` after a batch update on db)
2. Decide strategy:
   - default incremental mode (`onlyMissing=true`)
   - full re-embedding (`onlyMissing=false`) only when you need to rebuild everything (for example after embedding model changes)
3. Run the CLI command for your scope:
   - all missing: `./pmc regenerate-embeddings --base-url http://localhost:8080`
   - missing for one type: `./pmc regenerate-embeddings --node-type USER --only-missing --base-url http://localhost:8080`
   - full rebuild for one type: `./pmc regenerate-embeddings --node-type USER --only-missing=false --base-url http://localhost:8080`
   - explicit batch tuning (default is `1`): `./pmc regenerate-embeddings --node-type USER --only-missing=false --batch-size 4`
4. Choose execution style:
   - default mode waits and polls until completion
   - asynchronous fire-and-return: `./pmc regenerate-embeddings --node-type USER --no-wait`
   - manual status polling: `./pmc regenerate-embeddings-status --job-id <uuid>`
5. Use this after user ingest pipelines to backfill only missing user embeddings:
   - run users ingest (`ingest-users`)
   - then run `regenerate-embeddings --node-type USER --only-missing`

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- Start response returns a job descriptor containing `jobId`, `status`, `batchSize`, and scope fields.
- Final status should show `status=COMPLETED`, with `failed=0` in normal operation.
- Logs include maintenance execution summary with node type, mode, and batch size.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- Invalid request parameters: verify `nodeType` value and options.
- High `failed` count: inspect application logs for node-level embedding errors.

Note: This guide provides operational implementation guidance and is not legal advice.
