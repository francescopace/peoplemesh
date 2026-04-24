# Import Global Skills CSV

Import the global skills dictionary from CSV into PeopleMesh.

## Why this matters

Using a standard import path keeps canonical skill names and aliases consistent across environments.

## Audience

- Platform administrators and skills maintainers
- Integrators operating skill ingestion flows

## Prerequisites

- Authenticated user with `is_admin`
- A CSV file in one of the supported formats

## Supported CSV formats

PeopleMesh auto-detects the format from the first row (header). At minimum, a `name` (or equivalent) column must exist:

- Skills Base export: `category,name,...` (also accepted: `category_name,name,...`)
  - Optional columns: `aliases`
- ESCO export: `uri,title,preferred_label_en,skill_type,reuse_level`

## Procedure

Use the global import endpoint with the CSV as binary body:

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@./path/to/skills.csv" \
  "http://localhost:8080/api/v1/skills/import"
```

## Verification

- `GET /api/v1/skills?page=0&size=50` returns imported definitions
- `GET /api/v1/skills/suggest?q=jav` returns alias/name suggestions

## Troubleshooting

- `400 Bad Request`: header not recognized or malformed CSV
- `403 Forbidden`: missing `is_admin`
