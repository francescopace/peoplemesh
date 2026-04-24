# Generate Synthetic Data

Generate realistic development seed data for local environments and demos using the Stack Overflow Developer Survey 2025 as the backbone.

## Why this matters

Using realistic and reproducible synthetic data improves local testing quality and demo reliability without handling real personal data.

## Audience

- Developers setting up a local environment
- Anyone preparing a demo with realistic data

## What it generates

| Output | Source | File |
|--------|--------|------|
| Users (profiles, skills, tools, education) | randomuser.me + SO survey | `R__dev_seed_users.sql` |
| Internal job postings | SO survey roles + skills | `R__dev_seed_jobs.sql` |
| Groups and communities | SO survey skills + tags | `R__dev_seed_groups.sql` |
| Global skills dictionary seed (canonical names + aliases) | SO survey (all respondents) | `R__dev_seed_skill_catalog_so.sql` |

All generated data is scoped to a single company name and filtered by industry profile, so profiles, jobs, and groups are consistent with each other.

## Prerequisites

- Python 3.11+
- [Ollama](https://ollama.com) running locally (for embeddings)
- The SO survey CSV in `tools/data/stack-overflow-survey/`

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

### Pull the embedding model

```bash
ollama pull granite-embedding:30m
```

## Procedure

### Quick start

From the repo root:

```bash
python3 tools/data/create_fake_data.py
```

This command uses defaults: `--company-type it`, `--company-name "Acme Corp"`, 500 users, 50 jobs, and 100 groups.

The script writes SQL files into `src/main/resources/db/dev/` which Flyway picks up automatically when running with the `dev` profile.

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--company-name` | `Acme Corp` | Company name used across all generated data |
| `--company-type` | `it` | Industry profile (see below) |
| `--users` | `500` | Number of user profiles |
| `--jobs` | `50` | Number of internal job postings |
| `--groups` | `100` | Number of internal groups/events |
| `--ollama-base-url` | `http://localhost:11434` | Ollama endpoint |
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

Generate a small dataset without embeddings (Ollama not required, embeddings will be NULL):

```bash
python3 tools/data/create_fake_data.py \
  --users 50 \
  --jobs 10 \
  --groups 20 \
  --ollama-base-url http://localhost:1
```

## Data sources

| Source | What it provides | License |
|--------|-----------------|---------|
| [Stack Overflow Developer Survey 2025](https://survey.stackoverflow.co/2025/) | Skills, roles, tools, education, work mode distributions by industry (49k respondents) | [ODbL](https://opendatacommons.org/licenses/odbl/1-0/) |
| [randomuser.me](https://randomuser.me) | Synthetic user identities (name, email, photo, location) | Free / open API |

Fields that the SO survey does not cover (hobbies, sports, causes, personality) use small synthetic pools.

## Verification

- Generated SQL files exist in `src/main/resources/db/dev/`.
- Running the app in `dev` profile applies generated migrations.
- Seeded users/jobs/groups are visible via local API/UI after startup.

## Troubleshooting

- `survey_results_public.csv` not found: download it into `tools/data/stack-overflow-survey/`.
- Embedding generation errors: verify Ollama is running and reachable at `--ollama-base-url`.
- No output files generated: run from repository root or set `--workspace` explicitly.
