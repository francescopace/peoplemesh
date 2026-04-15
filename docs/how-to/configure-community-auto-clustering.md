# Configure Community Discovery Auto-Clustering

This guide configures the PeopleMesh feature that automatically clusters people
into semantic groups/communities ("auto-clustering") using profile embeddings.
It is an application feature for discovery UX, not infrastructure/service clustering.

## Why this matters

Clustering configuration should be governed to reduce profiling risk and improve explainability:

- [GDPR Art. 5(1)(a)](https://eur-lex.europa.eu/eli/reg/2016/679/oj): support fairness and transparency in automated grouping behavior.
- [GDPR Art. 22](https://eur-lex.europa.eu/eli/reg/2016/679/oj): assess safeguards where clustering may inform significant automated decisions.

## Audience

- Platform administrators
- Data and relevance maintainers

## Prerequisites

- Embeddings available for user profiles
- Maintenance endpoint access (`X-Maintenance-Key`)
- Capacity planning for periodic clustering runs

## Procedure

1. Enable group auto-clustering:
   - set `CLUSTERING_ENABLED=true`
2. Tune clustering parameters as needed:
   - `peoplemesh.clustering.k`
   - `peoplemesh.clustering.min-cluster-size`
   - `peoplemesh.clustering.max-centroid-distance`
3. Restart the application after config updates.
4. Trigger an auto-clustering run manually via CLI:
   - `./pmc run-clustering --base-url http://localhost:8080`
5. Schedule periodic runs (typically weekly).

Note: Equivalent REST APIs are available for B2B integrations; see [`../reference/api.md`](../reference/api.md).

## Verification

- New or updated community/group nodes are generated from cluster outputs.
- Cluster sizes align with expected lower bound.
- Names are produced and visible in UI/query results.

## Troubleshooting

- For CLI build/configuration/usage and common runtime issues (`./pmc` not found, timeout/connection errors, `401`/`403`), see [`../operations/maintenance.md`](../operations/maintenance.md).
- Very small/no clusters: reduce `k` or lower minimum cluster size.
- Noisy cluster quality: tighten max centroid distance threshold.
- Long runtime: run during off-peak hours and monitor resource usage.

Note: This guide provides operational implementation guidance and is not legal advice.
