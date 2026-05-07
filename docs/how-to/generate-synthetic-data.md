# Generate Synthetic Data

Generate realistic synthetic SQL seeds or PMC ingest JSON payloads for local environments and demos using the Stack Overflow Developer Survey 2025 as the backbone.

## Why this matters

Using realistic and reproducible synthetic data improves local testing quality and demo reliability without handling real personal data.

## Audience

- Developers setting up a local environment
- Anyone preparing a demo with realistic data

## What it generates

| Output | Source | File |
|--------|--------|------|
| Users (profiles, skills, tools, education) | randomuser.me + SO survey | `R__dev_seed_users.sql` or `users-001.json`, `users-002.json`, ... |
| Internal job postings | SO survey roles + skills | `R__dev_seed_jobs.sql` or `nodes-001.json`, `nodes-002.json`, ... |
| Groups and communities | SO survey skills + tags | `R__dev_seed_groups.sql` or `nodes-001.json`, `nodes-002.json`, ... |
| Global skills dictionary seed (canonical names + aliases) | SO survey (all respondents) | `R__dev_seed_skill_catalog_so.sql` |

All generated data is scoped to a single company name and filtered by industry profile, so profiles, jobs, and groups are consistent with each other.

## Prerequisites

- Python 3.11+
- The SO survey CSV in `tools/data/stack-overflow-survey/`
- One OpenAI-compatible embedding endpoint for SQL generation:
  - [Ollama](https://ollama.com) running locally at the default `http://localhost:11434/v1`
  - or the real OpenAI API with `OPENAI_API_KEY` and `--base-url https://api.openai.com/v1`

JSON generation with `--json` does not require any embedding backend.

### Download the survey data

The CSV is not committed to git (140 MB). Download it once:

```bash
cd tools/data/stack-overflow-survey
curl -L -o survey.zip "https://survey.stackoverflow.co/2025/download"
unzip survey.zip
rm survey.zip
```

If the direct link does not work, download manually from
<https://survey.stackoverflow.co/2025/> (Methodology section, "Download" button)
and place `survey_results_public.csv` inside `tools/data/stack-overflow-survey/`.

### Pull the Ollama embedding model

```bash
ollama pull granite-embedding:30m
```

## Procedure

### Quick start

From the repo root:

```bash
python3 tools/data/create_fake_data.py
```

This command uses defaults: `--company-type it`, `--company-name "Acme Corp"`, 500 users, 50 jobs, 100 groups, `--base-url http://localhost:11434/v1`, and `--embedding-model granite-embedding:30m`.

The script writes SQL files into `tools/data/sql/`. By default it targets Ollama's OpenAI-compatible endpoint at `http://localhost:11434/v1`.
Both `tools/data/sql/` and `tools/data/json/` are ignored by git, so generated artifacts stay local unless you copy them elsewhere.

To generate SQL using the real OpenAI embeddings API instead:

```bash
OPENAI_API_KEY=... python3 tools/data/create_fake_data.py \
  --base-url https://api.openai.com/v1 \
  --embedding-model text-embedding-3-small
```

To generate `pmc`-compatible JSON payloads instead of SQL:

```bash
python3 tools/data/create_fake_data.py --json
```

With `--json`, the script skips embeddings and writes batch files under `tools/data/json/`.
Each generated file stays within the current ingest limit of 100 records per request.

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--company-name` | `Acme Corp` | Company name used across all generated data |
| `--company-type` | `it` | Industry profile (see below) |
| `--users` | `500` | Number of user profiles |
| `--jobs` | `50` | Number of internal job postings |
| `--groups` | `100` | Number of internal groups/events |
| `--base-url` | `http://localhost:11434/v1` | OpenAI-compatible embeddings endpoint |
| `--api-key` | `OPENAI_API_KEY` env | API key for the embeddings endpoint when required |
| `--embedding-model` | `granite-embedding:30m` | Embedding model |
| `--json` | `false` | Write `pmc` ingest JSON payload batches and skip embeddings |
| `--seed` | `42` | Random seed for deterministic output |
| `--workspace` | `.` | Repo root (auto-detected via `pom.xml`) |

### Company types

Each type filters the SO survey by industry, so skills, roles, and tool distributions reflect what developers in that sector actually use.

| Type | SO industries included |
|------|----------------------|
| `it` | Software Development, Internet/Telecomm, Computer Systems Design |
| `finance` | Fintech, Banking/Financial Services, Insurance |
| `public_administration` | Government |
| `education` | Higher Education |
| `healthcare` | Healthcare |
| `manufacturing` | Manufacturing |
| `energy` | Energy |
| `logistics` | Transportation, Supply Chain |
| `retail` | Retail and Consumer Services |
| `media` | Media & Advertising Services |

### Examples

Generate data for a finance company:

```bash
python3 tools/data/create_fake_data.py \
  --company-type finance \
  --company-name "Global Bank" \
  --users 300 \
  --jobs 30
```

Generate `pmc`-compatible JSON batches without embeddings:

```bash
python3 tools/data/create_fake_data.py \
  --json \
  --users 50 \
  --jobs 10 \
  --groups 20
```

Generate SQL with OpenAI embeddings:

```bash
OPENAI_API_KEY=... python3 tools/data/create_fake_data.py \
  --base-url https://api.openai.com/v1 \
  --embedding-model text-embedding-3-small \
  --company-type it \
  --company-name "Acme OpenAI Demo"
```

## Data sources

| Source | What it provides | License |
|--------|-----------------|---------|
| [Stack Overflow Developer Survey 2025](https://survey.stackoverflow.co/2025/) | Skills, roles, tools, education, work mode distributions by industry (49k respondents) | [ODbL](https://opendatacommons.org/licenses/odbl/1-0/) |
| [randomuser.me](https://randomuser.me) | Synthetic user identities (name, email, photo, location) | Free / open API |

Fields that the SO survey does not cover (hobbies, sports, causes, personality) use small synthetic pools.

## Verification

- Generated SQL files exist in `tools/data/sql/`.
- Generated JSON payload batches exist in `tools/data/json/` when using `--json`.
- The generated SQL can be reviewed, loaded manually, or copied into a Flyway seed location if needed.

## Troubleshooting

- `survey_results_public.csv` not found: download it into `tools/data/stack-overflow-survey/`.
- Embedding generation errors with a local endpoint: verify it is running and reachable at `--base-url` (default `http://localhost:11434/v1`).
- Embedding generation errors with OpenAI: verify `OPENAI_API_KEY` is set, `--base-url https://api.openai.com/v1`, and the selected model supports 384-dimension embeddings.
- `pmc ingest-users` / `pmc ingest-nodes` rejects large payloads: use the batch files produced by `--json` one by one.
- No output files generated: run from repository root or set `--workspace` explicitly.
