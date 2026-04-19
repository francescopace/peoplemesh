# Embedding Model Evaluation

This page documents how PeopleMesh evaluates embedding model candidates and how to interpret results from `tools/eval_search_quality.py`.

## Why this exists

- Keep model choices reproducible across contributors.
- Avoid decisions based on one-off runs or different datasets.
- Preserve the rationale for current defaults.

## Evaluation protocol

Use the same protocol for every candidate model:

1. Align the full embedding pipeline to the candidate:
   - DB vector dimension (`V1__init.sql`)
   - backend embedding validation (`EmbeddingService`)
   - synthetic data generator defaults (`create_fake_data.py`)
   - evaluation model default (`eval_search_quality.py`)
2. Regenerate synthetic seeds with the candidate model.
3. Reload local dev DB (for example via `make clean start`).
4. Run full battery with fixed seed:

```bash
python tools/eval_search_quality.py --seed 42
```

The script always includes:
- direct embedding retrieval
- API `/api/v1/matches` (embedding-neighbors + strict relevance)
- API `/api/v1/matches/me` strict relevance
- payload-vs-Java embedding text divergence

## Decision criteria

Primary (must pass):
- `overall_global` should be `GREEN`
- `direct_overall` should be at least `GREEN` for stable direct retrieval quality

Secondary (tie-breakers):
- API strict metrics (`P@10`, `NDCG@10`, `MRR`)
- My Mesh strict metrics (`P@10`, `NDCG@10`, `MRR`)
- latency/cost in local and target runtime environments

Guardrails:
- Compare only runs with identical seed and dataset generation flow.
- Treat synthetic-data findings as directional; validate with real-world samples before production switch.

## Current snapshot (seed 42)

From consolidated local runs:

| Model | Overall | Direct P@10 | API Strict P@10 | My Mesh Strict P@10 |
|---|---|---:|---:|---:|
| `granite-embedding:30m` | GREEN | 0.463 | 0.950 | 0.950 |
| `text-embedding-3-small` | GREEN | 0.450 | 0.942 | 0.883 |
| `granite-embedding:278m` | YELLOW | 0.438 | 0.967 | 0.917 |
| `qwen3-embedding:0.6b` | YELLOW | 0.400 | 0.908 | 0.925 |

Current practical default remains `granite-embedding:30m`.

## Where raw numbers live

Contributors can keep local detailed run logs in:
- `ignored/eval-search-baselines.md` (gitignored, optional)

Only summary decisions and protocol should live in committed docs.
