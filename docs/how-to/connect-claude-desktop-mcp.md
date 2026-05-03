# Connect Claude Desktop to PeopleMesh MCP

Use this guide to connect Claude Desktop to the PeopleMesh MCP server using the built-in OAuth 2.1 flow.

## Why this matters

Claude Desktop requires a standards-compliant MCP authentication flow (OAuth metadata discovery, client registration, authorization code flow, and bearer token usage). This guide ensures the connector can authenticate reliably.

## Audience

- Platform administrators
- Developers testing MCP integrations
- Operators troubleshooting Claude Desktop connectivity

## Prerequisites

- PeopleMesh is running and reachable from the public internet over HTTPS
- A valid TLS certificate is configured on the public domain
- At least one login provider is configured (`google` or `microsoft`)
- You are logged in to `claude.ai` in your default system browser

## Procedure

### 1) Verify the server is running

```bash
curl -f https://<your-domain>/q/health
```

Expected: HTTP `200` with `"status":"UP"`.

### 2) Verify MCP OAuth discovery endpoints

```bash
curl -f https://<your-domain>/.well-known/oauth-protected-resource/mcp
curl -f https://<your-domain>/.well-known/oauth-authorization-server
```

Expected:

- Protected resource metadata contains `"resource":"https://<your-domain>/mcp"`
- Authorization server metadata contains:
  - `authorization_endpoint`
  - `token_endpoint`
  - `registration_endpoint`

### 3) Add the MCP connector in Claude Desktop

Use this server URL:

- `https://<your-domain>/mcp`

Do not use `/mcp/sse` for Claude Desktop unless explicitly required by your client version.

### 4) Complete OAuth in the browser

When Claude prompts for authentication:

1. Follow the browser redirect
2. Select the provider (if multiple providers are configured)
3. Complete login and consent
4. Return to Claude Desktop

### 5) Confirm tools are visible

After connection succeeds, Claude should list the PeopleMesh tools:

- `peoplemesh_get_my_profile`
- `peoplemesh_match`
- `peoplemesh_match_me`
- `peoplemesh_match_node`

## Verification

- `POST https://<your-domain>/mcp` without auth returns `401` and `WWW-Authenticate: Bearer ...`
- Claude Desktop shows PeopleMesh tools and lets you configure tool permissions
- Tool calls return normal MCP responses instead of connection errors

## Troubleshooting

- If Claude shows `Couldn't reach the MCP server`:
  - remove the connector and add it again
  - ensure you are logged in to `claude.ai` in the system browser
  - verify the domain and TLS certificate are valid
- If OAuth does not start:
  - verify `/.well-known/oauth-protected-resource/mcp`
  - verify `/.well-known/oauth-authorization-server`
- If login succeeds but tool calls fail:
  - verify unauthenticated `POST /mcp` returns `401` with `WWW-Authenticate` bearer challenge
  - verify your reverse proxy preserves HTTPS and host headers
- If provider selection page is empty:
  - configure at least one login provider in PeopleMesh (`google` or `microsoft`)

## Related docs

- MCP reference and tool contracts: [`../reference/mcp.md`](../reference/mcp.md)
- OIDC provider configuration: [`configure-oidc.md`](configure-oidc.md)
- Local bootstrap: [`../getting-started/quickstart.md`](../getting-started/quickstart.md)
