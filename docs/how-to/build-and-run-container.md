# Build and Run the Container

Build a local PeopleMesh image and run it with the minimum required production-style configuration.

## Audience

- Platform administrators
- Operators and SRE
- Developers validating container startup

## Prerequisites

- Docker running locally
- Reachable PostgreSQL instance
- Reachable Docling service
- At least one configured OIDC provider
- OpenAI API key (required in production profile)

## 1) Build the image

Use the standard make target:

```bash
make image
```

Optional custom tag:

```bash
make image IMAGE_NAME=peoplemesh:dev
```

## 2) Run the container (minimum required variables)

Example with explicit required variables:

```bash
docker run --rm \
  --name peoplemesh \
  -p 8080:8080 \
  -e DB_URL='jdbc:postgresql://host.docker.internal:5432/peoplemesh' \
  -e DB_USER='peoplemesh' \
  -e DB_PASSWORD='change-me' \
  -e OPENAI_API_KEY='sk-...' \
  -e CONSENT_TOKEN_SECRET='replace-with-32-plus-bytes' \
  -e SESSION_SECRET='replace-with-32-plus-bytes' \
  -e OAUTH_STATE_SECRET='replace-with-32-plus-bytes' \
  -e MAINTENANCE_API_KEY='replace-with-strong-shared-key' \
  -e CORS_ORIGINS='https://app.example.com' \
  -e OIDC_GOOGLE_CLIENT_ID='...' \
  -e OIDC_GOOGLE_CLIENT_SECRET='...' \
  -e DOCLING_URL='http://host.docker.internal:5001' \
  peoplemesh:local
```

`OIDC_GOOGLE_*` is an example; use any supported provider (`GOOGLE`, `MICROSOFT`) as long as at least one provider is configured.

## Required parameters checklist

These are required for a non-dev container run:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `CONSENT_TOKEN_SECRET`
- `SESSION_SECRET`
- `OAUTH_STATE_SECRET`
- `MAINTENANCE_API_KEY`
- `CORS_ORIGINS`
- At least one provider pair:
  - `OIDC_GOOGLE_CLIENT_ID` + `OIDC_GOOGLE_CLIENT_SECRET`, or
  - `OIDC_MICROSOFT_CLIENT_ID` + `OIDC_MICROSOFT_CLIENT_SECRET`

## Passing system properties (`-D...`)

The image already sets default container JVM options in `JAVA_OPTS_APPEND` (host/port/log manager).
To add your own system properties safely, prefer `JAVA_OPTS` so you do not overwrite those defaults.

Example:

```bash
docker run --rm \
  -p 8080:8080 \
  -e DB_URL='jdbc:postgresql://host.docker.internal:5432/peoplemesh' \
  -e DB_USER='peoplemesh' \
  -e DB_PASSWORD='change-me' \
  -e OPENAI_API_KEY='sk-...' \
  -e CONSENT_TOKEN_SECRET='replace-with-32-plus-bytes' \
  -e SESSION_SECRET='replace-with-32-plus-bytes' \
  -e OAUTH_STATE_SECRET='replace-with-32-plus-bytes' \
  -e MAINTENANCE_API_KEY='replace-with-strong-shared-key' \
  -e CORS_ORIGINS='https://app.example.com' \
  -e OIDC_GOOGLE_CLIENT_ID='...' \
  -e OIDC_GOOGLE_CLIENT_SECRET='...' \
  -e JAVA_OPTS='-Dquarkus.datasource.jdbc.max-size=30 -Dpeoplemesh.matching.result-limit=50 -Dpeoplemesh.notification.dry-run=false' \
  peoplemesh:local
```

You can configure most runtime keys either as environment variables (when mapped) or as system properties.
For the full list, see [`../reference/configuration.md`](../reference/configuration.md).

## Verification

- Open `http://localhost:8080`
- Check health: `GET /q/health`
- Check auth providers: `GET /api/v1/auth/providers`

## Troubleshooting

- Connection refused on startup: verify `DB_URL` and network reachability from the container.
- Login providers missing: verify OIDC client id/secret environment variables.
- Maintenance endpoints always forbidden: verify `MAINTENANCE_API_KEY` and `X-Maintenance-Key` header usage.
- CV import failures: verify `DOCLING_URL` is reachable from inside the container.
