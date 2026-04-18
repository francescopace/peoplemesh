# MCP Reference

PeopleMesh exposes a read-only MCP server over HTTP.
This reference follows PeopleMesh's security- and GDPR-first posture by design (authenticated access, no write tools, and explicit capability boundaries).

## Audience

- MCP client integrators
- AI platform developers
- Security reviewers validating read-only scope

## How to use this page

- Use this page to understand MCP endpoint, auth requirements, and tool contracts.
- For REST write flows, use [`api.md`](api.md) and the relevant how-to guides.

## Endpoint

- `POST /mcp` (Streamable HTTP)
- Legacy SSE compatibility path: `/mcp/sse`

## Authentication

All MCP tools require authentication.
Unauthenticated calls are rejected by HTTP authorization rules.

## Available tools

All tools are read-only and use the `peoplemesh_` prefix.
MCP handlers delegate to service-layer use cases and do not access repositories directly.

| Tool | Purpose |
|------|---------|
| `peoplemesh_get_my_profile` | Returns the authenticated user's profile as `ProfileSchema` JSON |
| `peoplemesh_match` | Returns matches from a `SearchQuery` JSON input (optional filters: `type`, `country`) |
| `peoplemesh_match_me` | Returns matches from the authenticated user's stored embedding |
| `peoplemesh_match_node` | Returns matches from a specific node embedding (`nodeId` + optional filters) |

## Out of scope (by design)

These capabilities are intentionally not exposed through MCP:

- Profile editing
- Node creation or mutation
- ATS job ingestion and maintenance operations

Use REST endpoints and the web application for write flows.
