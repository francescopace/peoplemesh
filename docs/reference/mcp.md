# MCP Reference

PeopleMesh exposes a read-only MCP server over HTTP.
This reference follows PeopleMesh's security- and GDPR-first posture by design (authenticated access, no write tools, and explicit capability boundaries).

## Audience

- MCP client integrators
- AI platform developers
- Security reviewers validating read-only scope

## How to Use This Page

- Use this page to understand MCP endpoint, auth requirements, and tool contracts.
- For REST write flows, use [`api.md`](api.md) and the relevant how-to guides.

## Endpoint

- `POST /mcp` (Streamable HTTP)
- Legacy SSE compatibility path: `/mcp/sse`

## Authentication

All MCP tools require authentication.
Unauthenticated calls are rejected by HTTP authorization rules.

For browser-mediated MCP clients, PeopleMesh exposes an OAuth login bootstrap endpoint:

- `GET /api/v1/auth/mcp/login`

If multiple login providers are configured, this endpoint renders a provider chooser.
After successful sign-in, the flow completes at:

- `GET /api/v1/auth/mcp/complete`

For OAuth 2.1 MCP clients, PeopleMesh also exposes:

- `GET /.well-known/oauth-protected-resource/mcp`
- `GET /.well-known/oauth-authorization-server`
- `POST /oauth/register` (dynamic client registration)
- `GET /oauth/authorize`
- `POST /oauth/token`

## Available tools

All tools are read-only and use the `peoplemesh_` prefix.
MCP handlers delegate to service-layer use cases and do not access repositories directly.

| Tool | Purpose |
|------|---------|
| `peoplemesh_get_my_profile` | Returns the authenticated user's profile as `ProfileSchema` JSON |
| `peoplemesh_match_prompt` | Returns parsed query metadata and ranked results from a natural-language prompt (optional filters: `type`, `country`) |
| `peoplemesh_match_me` | Returns matches from the authenticated user's stored embedding |

## `peoplemesh_match_prompt` contract

`peoplemesh_match_prompt` expects the original natural-language user request in `query`.

Do:

- Pass the user's request verbatim instead of pre-parsing it client-side.
- Use `type=PEOPLE` only when the user explicitly wants people or candidates.
- Leave `country` empty unless the user explicitly asked for a country or location constraint.
- Let PeopleMesh interpret the prompt with its server-side parser and ranking logic.

Do not:

- Invent country filters from locale, user profile, session, or prior context.
- Rewrite or relax the user's criteria before calling the tool.
- Convert the prompt into your own JSON schema first.

### Example

Example tool call for a people search:

```json
{
  "query": "Find a Java developer with Kubernetes experience who speaks English",
  "type": "PEOPLE",
  "country": ""
}
```

The response is a `SearchResponse` JSON object with:

- `parsedQuery`: the backend-generated `SearchQuery`
- `results`: ranked matches

This means MCP clients do not need to know the internal `SearchQuery` schema for normal search flows.

### Relationship to REST

- MCP search uses `peoplemesh_match_prompt` and keeps query parsing server-side.
- Structured `SearchQuery` requests are still available on the REST API at `POST /api/v1/matches`.
- Use the structured REST endpoint only for first-party clients or advanced integrations that intentionally manage `SearchQuery` directly.

## Out of scope (by design)

These capabilities are intentionally not exposed through MCP:

- Profile editing
- Node creation or mutation
- Job ingestion and maintenance operations

Use REST endpoints and the web application for write flows.
