# Quick Start

This guide helps you run PeopleMesh locally with the default development setup.

For production deployment hardening, see [`production-baseline.md`](production-baseline.md).

## Audience

- Developers
- Contributors evaluating the project locally

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker
- Node.js 20+ (frontend tests only)

## Procedure

```bash
make start
# or
mvn quarkus:dev
```

In dev mode, DevServices auto-starts:

- PostgreSQL with pgvector
- Docling service

Ollama is expected to be available locally for dev LLM/embedding flows.

## Verification

Once the app is running:

- Open `http://localhost:8080`
- Check the health endpoint: `GET /q/health`
- Optional (dev mode only): `GET /q/dev-ui`

## Build and run the container image

```bash
make image
docker run --rm -p 8080:8080 peoplemesh:local
```

Notes:

- The image uses `src/main/docker/Dockerfile.jvm`.
- Override the image name if needed: `make image IMAGE_NAME=peoplemesh:dev`.
- For required environment variables and `-D` system properties, see [`../how-to/build-and-run-container.md`](../how-to/build-and-run-container.md).

## First useful endpoints

- `GET /api/v1/info`
- `GET /api/v1/me`
- `POST /api/v1/matches/prompt`
- `POST /mcp`

For full endpoint details, see [`../reference/api.md`](../reference/api.md).

## Troubleshooting

- If startup fails on database initialization, ensure Docker is running.
- If AI-dependent flows fail in dev mode, verify local Ollama availability.
- If OAuth login is unavailable, check provider credentials in configuration.
