# Import a Skill Catalog CSV

Import a skill catalog from CSV into PeopleMesh and generate embeddings in one operation.

## Why this matters

Using a standard import path keeps skill definitions consistent and traceable across environments.

## Prerequisites

- Authenticated user with `is_admin`
- A target catalog id (create one first if needed via `POST /api/v1/skills`)
- A CSV file in one of the supported formats

## Supported CSV formats

PeopleMesh auto-detects the format from the first row (header):

- Skills Base export: `category,name,...` (also accepted: `category_name,name,...`)
  - Optional columns: `lxp_recommendation` (or `lxp`), `aliases`
- ESCO export: `uri,title,preferred_label_en,skill_type,reuse_level`

## Import

Use the catalog import endpoint with the CSV as binary body:

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@./path/to/skills.csv" \
  "http://localhost:8080/api/v1/skills/<catalogId>/import"
```

## Verify

- `GET /api/v1/skills/<catalogId>/definitions` returns imported definitions
- `GET /api/v1/skills/<catalogId>/categories` returns populated categories

## Troubleshooting

- `400 Bad Request`: header not recognized or malformed CSV
- `403 Forbidden`: missing `is_admin`
- `404 Not Found`: catalog id does not exist
