# Run PeopleMesh with Docker Compose

Run PeopleMesh and its external dependencies (PostgreSQL + pgvector, Ollama, Docling) with a single Docker Compose command.

## Audience

- Platform administrators
- Operators and SRE
- Developers validating full container startup

## Where the compose file is

The stack definition is in:

- `tools/docker-compose.dependencies.yml`

If you are reading docs outside the repository checkout, use:

- <https://github.com/francescopace/peoplemesh/blob/main/tools/docker-compose.dependencies.yml>

## Prerequisites

- Docker running locally
- At least one OIDC provider configured (Google or Microsoft)

## 1) Prepare environment variables

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

- `PEOPLEMESH_IMAGE` (default: `frapax/peoplemesh:latest`)
- `DOCLING_IMAGE` (default: `ghcr.io/docling-project/docling-serve:latest`)
- `PEOPLEMESH_FRONTEND_ENABLED` (default: `true`)

## 2) Start the stack

```bash
docker compose -f tools/docker-compose.dependencies.yml up -d
```

## 3) Pull default Ollama models (first run only)

```bash
docker exec -it peoplemesh-ollama ollama pull granite4:3b
docker exec -it peoplemesh-ollama ollama pull granite-embedding:30m
```

## 4) Verify runtime

```bash
curl -f http://localhost:8080/q/health
curl -f http://localhost:8080/api/v1/auth/providers
```

## 5) Stop or clean up

```bash
docker compose -f tools/docker-compose.dependencies.yml down
```

Remove volumes too (full local reset):

```bash
docker compose -f tools/docker-compose.dependencies.yml down -v
```

## Troubleshooting

- If app startup fails, check logs: `docker compose -f tools/docker-compose.dependencies.yml logs peoplemesh`
- If OIDC providers are empty, verify provider env vars in `.env`
- If CV import fails, check Docling logs: `docker compose -f tools/docker-compose.dependencies.yml logs docling`
- If AI features fail, ensure Ollama models are pulled and ready

## Related docs

- Build/run with plain `docker run`: [`build-and-run-container.md`](build-and-run-container.md)
- Full configuration reference: [`../reference/configuration.md`](../reference/configuration.md)
