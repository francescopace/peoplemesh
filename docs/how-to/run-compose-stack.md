# Run PeopleMesh with Docker Compose

Run PeopleMesh and its external dependencies (PostgreSQL + pgvector, Ollama, Docling) with a single Docker Compose command.

## Why this matters

Running the full dependency stack locally allows end-to-end validation of startup, authentication, import, and AI-assisted features.

## Audience

- Platform administrators
- Operators and SRE
- Developers validating full container startup

## Where the compose file is

The stack definition is in:

- `tools/compose/docker-compose.dependencies.yml`

If you are reading docs outside the repository checkout, use:

- <https://github.com/francescopace/peoplemesh/blob/main/tools/compose/docker-compose.dependencies.yml>

## Prerequisites

- Docker running locally
- At least one OIDC provider configured (Google or Microsoft)

## Procedure

### 1) Prepare environment variables

Create a `.env` file in the repository root:

```bash
CONSENT_TOKEN_SECRET=replace-with-32-plus-bytes
SESSION_SECRET=replace-with-32-plus-bytes
OAUTH_STATE_SECRET=replace-with-32-plus-bytes
MAINTENANCE_API_KEY=replace-with-strong-shared-key
CORS_ORIGINS=http://localhost:8080
OIDC_GOOGLE_CLIENT_ID=...
OIDC_GOOGLE_CLIENT_SECRET=...
# or:
# OIDC_MICROSOFT_CLIENT_ID=...
# OIDC_MICROSOFT_CLIENT_SECRET=...
```

Optional overrides:

- `PEOPLEMESH_IMAGE` (default: `frapax/peoplemesh:main`)
- `DOCLING_IMAGE` (default: `ghcr.io/docling-project/docling-serve:v1.16.1`)
- `PEOPLEMESH_FRONTEND_ENABLED` (default: `true`)

### 2) Start the stack

```bash
docker compose -f tools/compose/docker-compose.dependencies.yml up -d
```

### 3) Pull default Ollama models (first run only)

```bash
docker exec -it peoplemesh-ollama ollama pull granite4:3b
docker exec -it peoplemesh-ollama ollama pull granite-embedding:30m
```

### 4) Verify runtime

```bash
curl -f http://localhost:8080/q/health
curl -f http://localhost:8080/api/v1/info
```

### 5) Stop or clean up

```bash
docker compose -f tools/compose/docker-compose.dependencies.yml down
```

Remove volumes too (full local reset):

```bash
docker compose -f tools/compose/docker-compose.dependencies.yml down -v
```

## Verification

- Health endpoint responds successfully: `GET /q/health`.
- Info endpoint responds successfully: `GET /api/v1/info` (including `authProviders`).
- Core services (`peoplemesh`, `postgres`, `ollama`, `docling`) are running in Compose.

## Troubleshooting

- If app startup fails, check logs: `docker compose -f tools/compose/docker-compose.dependencies.yml logs peoplemesh`
- If OIDC providers are empty, verify provider env vars in `.env`
- If CV import fails, check Docling logs: `docker compose -f tools/compose/docker-compose.dependencies.yml logs docling`
- If AI features fail, ensure Ollama models are pulled and ready

## Related docs

- Build/run with plain `docker run`: [`build-and-run-container.md`](build-and-run-container.md)
- Full configuration reference: [`../reference/configuration.md`](../reference/configuration.md)
