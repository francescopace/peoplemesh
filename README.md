[![CI](https://img.shields.io/github/actions/workflow/status/francescopace/peoplemesh/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white&color=2EA043)](https://github.com/francescopace/peoplemesh/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/francescopace/peoplemesh/graph/badge.svg?token=YXGLL6C7LH)](https://codecov.io/gh/francescopace/peoplemesh)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-4F46E5.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Docker Hub](https://img.shields.io/badge/Docker%20Hub-frapax%2Fpeoplemesh-2496ED?logo=docker&logoColor=white)](https://hub.docker.com/r/frapax/peoplemesh)
[![Red Hat AI Quickstart](https://img.shields.io/badge/Red%20Hat-AI%20Quickstart-EE0000?logo=redhat&logoColor=white)](https://docs.redhat.com/en/learn/ai-quickstarts/rh-peoplemesh)

# PeopleMesh

*The right match in your mesh.*

PeopleMesh is the AI-powered matching layer for modern organizations. It helps people discover the right colleagues, internal opportunities, communities, and projects through semantic search that understands context, not just keywords.

By combining embeddings with metadata-based ranking, PeopleMesh surfaces high-signal matches faster, cuts through noise, and improves internal mobility and collaboration.

Built privacy-first, PeopleMesh includes granular consent controls, configurable retention, and GDPR-aligned data rights workflows. Available via web app, API, and MCP integrations for AI assistants.

Open-source at the core. Enterprise-ready in practice. Never built on personal data monetization.

**Red Hat AI Quickstart**
> PeopleMesh is now included in the [Red Hat AI Quickstarts catalog](https://docs.redhat.com/en/learn/ai-quickstarts/rh-peoplemesh) as **Deploy AI-powered talent discovery with semantic search**.  
> The official quickstart packaging, deployment assets, and installation guides are available in [`rh-ai-quickstart/peoplemesh`](https://github.com/rh-ai-quickstart/peoplemesh).

## Why PeopleMesh

In large organizations, discovery is fragmented across chat, spreadsheets, and disconnected systems.
PeopleMesh provides one search surface: describe what you need in natural language and get ranked matches across all node types.

## What You Can Do

- Search colleagues, internal opportunities, communities, and initiatives with one prompt (from web UI, API, or MCP clients)
- Build and enrich profiles (manual, CV import, OAuth import)
- Manage skill catalogs and self-assessments
- Discover relevant communities, projects, and initiatives
- Access PeopleMesh from the web app, API integrations, ChatGPT/Claude via MCP, or any AI agent that supports MCP
- Use MCP search through a prompt-based tool so query parsing stays server-side and consistent with the web experience

## How It Works

PeopleMesh models the organization as a single graph-like mesh where each entity is a node (people, opportunities, groups, communities, projects, initiatives, and more).
Each node is converted into an embedding vector that captures semantic meaning from its content and metadata.

When a user searches, the query is embedded in the same vector space and matched against nodes using vector similarity (cosine similarity), then ranked to return the most relevant results.

## Trust, Security, and GDPR by Design

PeopleMesh is built with security and privacy controls as first-class product constraints:

- Granular consent management by scope with user-controlled revoke/re-grant flows
- GDPR rights support in product flows (data export, account deletion, processing restriction)
- Configurable retention enforcement
- Pseudonymized audit trails (hashed identifiers, no profile content in logs)
- Protected maintenance surfaces (`X-Maintenance-Key` and optional CIDR allowlists)

## Quick Start

Run locally with:

```bash
make start
```

Requirements: Java 25+, Maven 3.9+, Docker.

DevServices auto-starts PostgreSQL (pgvector) and Docling.
The base runtime configuration targets OpenAI through LangChain4j.
The local `dev` profile is environment-driven: by default it uses a local `Ollama` endpoint through its OpenAI-compatible API and keeps `Docling` enabled for CV import.

To run the same `dev` profile with OpenAI-backed seeds and PDF import:

```bash
OPENAI_API_KEY=... \
CV_IMPORT_PROVIDER=openai \
DOCLING_DEVSERVICES_ENABLED=false \
DOCLING_BASE_URL=http://localhost:5001 \
DEV_SEED_PROFILE=openai \
mvn quarkus:dev
```

This keeps the `dev` profile active, switches CV import to `openai`, loads the `openai` seed set, disables Docling DevServices, and provides the Docling base URL explicitly for extension bootstrap.

## Documentation

Technical documentation is organized in [`docs/README.md`](docs/README.md).

Open-source governance and legal documents:

- [ROADMAP.md](ROADMAP.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md)
- [SUPPORT.md](SUPPORT.md)

Medium articles (friend links, no paywall):

- [Part 1: We Are Dehumanizing Our Companies. AI Doesn't Have To.](https://medium.com/@francesco.pace/we-are-dehumanizing-our-companies-ai-doesnt-have-to-2aeee4d4cac2?sk=b51ed4e0a0332dd395fbdea8485d7cba)
- [Part 2: How to Build a Semantic Discovery Layer for Your Company](https://medium.com/@francesco.pace/how-to-build-a-semantic-discovery-layer-for-your-company-8ffb1f9ac011?sk=31f9e79d1b73d46b196b4ad9b3e3c969)

## License

Apache License 2.0.
See [LICENSE](LICENSE).

## Enterprise Support and Plugins

PeopleMesh core remains fully open-source under Apache-2.0.
Official enterprise support and proprietary enterprise plugins/connectors (for example LDAP, Slack, LinkedIn, Workday) are available separately for organizations that need them.
For enterprise inquiries, see [SUPPORT.md](SUPPORT.md).

## Author

- Author: [Francesco Pace](mailto:francesco.pace@peoplemesh.org)
- Website: [peoplemesh.org](https://peoplemesh.org)
- Repo: [github.com/francescopace/peoplemesh](https://github.com/francescopace/peoplemesh)