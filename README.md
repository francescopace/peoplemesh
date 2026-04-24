[![CI](https://img.shields.io/github/actions/workflow/status/francescopace/peoplemesh/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white&color=2EA043)](https://github.com/francescopace/peoplemesh/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/francescopace/peoplemesh/graph/badge.svg?token=YXGLL6C7LH)](https://codecov.io/gh/francescopace/peoplemesh)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-4F46E5.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Docker Hub](https://img.shields.io/badge/Docker%20Hub-frapax%2Fpeoplemesh-2496ED?logo=docker&logoColor=white)](https://hub.docker.com/r/frapax/peoplemesh)

# PeopleMesh

*The right match in your mesh.*

PeopleMesh is the AI-powered matching layer for modern organizations. It helps people discover the right colleagues, internal opportunities, communities, and projects through semantic search that understands context, not just keywords.

By combining embeddings with metadata-based ranking, PeopleMesh surfaces high-signal matches faster, cuts through noise, and improves internal mobility and collaboration.

Built privacy-first, PeopleMesh includes granular consent controls, configurable retention, and GDPR-aligned data rights workflows. Available via web app, API, and MCP integrations for AI assistants.

Open-source at the core. Enterprise-ready in practice. Never built on personal data monetization.

## Why PeopleMesh

In large organizations, discovery is fragmented across chat, spreadsheets, and disconnected systems.
PeopleMesh provides one search surface: describe what you need in natural language and get ranked matches across all node types.

## What You Can Do

- Search colleagues, internal opportunities, communities, and initiatives with one prompt (from web UI, API, or MCP clients)
- Build and enrich profiles (manual, CV import, OAuth import)
- Manage skill catalogs and self-assessments
- Discover relevant communities, projects, and initiatives
- Access PeopleMesh from the web app, API integrations, ChatGPT/Claude via MCP, or any AI agent that supports MCP

## How It Works

PeopleMesh models the organization as a single graph-like mesh where each entity is a node (people, opportunities, groups, communities, projects, initiatives, and more).
Each node is converted into an embedding vector that captures semantic meaning from its content and metadata.

When a user searches, the query is embedded in the same vector space and matched against nodes using vector similarity (cosine similarity), then ranked to return the most relevant results.

## Trust, Security, and GDPR by Design

PeopleMesh is built with security and privacy controls as first-class product constraints:

- Granular consent management by scope with user-controlled revoke/re-grant flows
- GDPR rights support in product flows (data export, account deletion, processing restriction)
- Configurable retention enforcement and consent-token lifecycle maintenance
- Pseudonymized audit trails (hashed identifiers, no profile content in logs)
- Protected maintenance surfaces (`X-Maintenance-Key` and optional CIDR allowlists)

## Quick Start

Run locally with:

```bash
make start
```

Requirements: Java 21+, Maven 3.9+, Docker.

DevServices auto-starts PostgreSQL (pgvector) and Docling.
Ensure Ollama is available locally for LLM inference and embeddings.

## Documentation

Technical documentation is organized in [`docs/README.md`](docs/README.md).

Open-source governance and legal documents:

- [ROADMAP.md](ROADMAP.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md)
- [SUPPORT.md](SUPPORT.md)

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