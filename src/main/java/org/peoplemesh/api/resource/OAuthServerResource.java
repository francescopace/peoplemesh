package org.peoplemesh.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.peoplemesh.domain.dto.AuthProvidersDto;
import org.peoplemesh.service.McpOAuthService;
import org.peoplemesh.service.OAuthLoginService;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/oauth")
public class OAuthServerResource {

    @Inject
    McpOAuthService mcpOAuthService;

    @Inject
    OAuthLoginService oAuthLoginService;

    @jakarta.ws.rs.core.Context
    UriInfo uriInfo;

    @GET
    @Path("/authorize")
    public Response authorize(
            @QueryParam("response_type") String responseType,
            @QueryParam("client_id") String clientId,
            @QueryParam("redirect_uri") String redirectUri,
            @QueryParam("state") String state,
            @QueryParam("code_challenge") String codeChallenge,
            @QueryParam("code_challenge_method") String codeChallengeMethod,
            @QueryParam("scope") String scope
    ) {
        if (!"code".equals(responseType)) {
            return authorizationErrorRedirect(redirectUri, state, "unsupported_response_type", "Only code is supported");
        }
        if (clientId == null || clientId.isBlank()) {
            return authorizationErrorRedirect(redirectUri, state, "invalid_client", "Missing client_id");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("redirect_uri is required").build();
        }
        if (codeChallenge != null && !codeChallenge.isBlank() && !"S256".equals(codeChallengeMethod)) {
            return authorizationErrorRedirect(redirectUri, state, "invalid_request", "Only S256 code_challenge_method is supported");
        }
        var requestOpt = mcpOAuthService.createAuthorizationRequest(clientId, redirectUri, state, codeChallenge, scope);
        if (requestOpt.isEmpty()) {
            return authorizationErrorRedirect(redirectUri, state, "invalid_client", "Invalid client or redirect_uri");
        }
        String requestId = requestOpt.get().requestId();
        AuthProvidersDto providers = oAuthLoginService.providers();
        List<String> loginProviders = providers.loginProviders();
        if (loginProviders == null || loginProviders.isEmpty()) {
            return authorizationErrorRedirect(redirectUri, state, "access_denied", "No login provider configured");
        }
        if (loginProviders.size() == 1) {
            URI loginUri = buildProviderLoginUri(loginProviders.getFirst(), requestId);
            return Response.status(Response.Status.FOUND).location(loginUri).build();
        }
        return Response.ok(renderProviderChooser(loginProviders, requestId))
                .type(MediaType.TEXT_HTML)
                .build();
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(Map<String, Object> request) {
        String clientName = asString(request, "client_name", "MCP Client");
        List<String> redirectUris = asStringList(request, "redirect_uris");
        List<String> grantTypes = asStringList(request, "grant_types");
        String authMethod = asString(request, "token_endpoint_auth_method", "none");
        McpOAuthService.RegisteredClient client = mcpOAuthService.registerClient(
                clientName,
                redirectUris,
                grantTypes,
                authMethod
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", client.clientId());
        if (client.clientSecret() != null) {
            body.put("client_secret", client.clientSecret());
        }
        body.put("client_name", client.clientName());
        body.put("redirect_uris", client.redirectUris());
        body.put("grant_types", client.grantTypes());
        body.put("token_endpoint_auth_method", client.tokenEndpointAuthMethod());
        body.put("response_types", List.of("code"));
        return Response.status(Response.Status.CREATED)
                .entity(body)
                .header("Cache-Control", "no-store")
                .build();
    }

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(
            @FormParam("grant_type") String grantType,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("code_verifier") String codeVerifier
    ) {
        if (!"authorization_code".equals(grantType)) {
            return tokenError("unsupported_grant_type", "Only authorization_code is supported", 400);
        }
        var tokenOpt = mcpOAuthService.exchangeAuthorizationCode(
                clientId,
                clientSecret,
                code,
                redirectUri,
                codeVerifier
        );
        if (tokenOpt.isEmpty()) {
            return tokenError("invalid_grant", "Invalid code, client, redirect_uri, or verifier", 400);
        }
        return Response.ok(tokenOpt.get())
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build();
    }

    private URI buildProviderLoginUri(String provider, String requestId) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("api/v1/auth/login")
                .path(provider)
                .queryParam("intent", OAuthLoginService.INTENT_MCP_OAUTH)
                .queryParam("ctx", requestId)
                .build();
    }

    private String renderProviderChooser(List<String> providers, String requestId) {
        String options = providers.stream()
                .map(provider -> "<li><a href=\"" + buildProviderLoginUri(provider, requestId) + "\">Continue with "
                        + escapeHtml(labelForProvider(provider)) + "</a></li>")
                .reduce("", String::concat);
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>PeopleMesh OAuth Authorization</title>
                </head>
                <body>
                  <h1>Authorize PeopleMesh</h1>
                  <p>Select a provider to continue.</p>
                  <ul>
                """ + options + """
                  </ul>
                </body>
                </html>
                """;
    }

    private Response authorizationErrorRedirect(String redirectUri, String state, String error, String description) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error + ": " + description)
                    .build();
        }
        StringBuilder location = new StringBuilder(redirectUri);
        location.append(redirectUri.contains("?") ? "&" : "?");
        location.append("error=").append(urlEncode(error));
        location.append("&error_description=").append(urlEncode(description));
        if (state != null && !state.isBlank()) {
            location.append("&state=").append(urlEncode(state));
        }
        return Response.status(Response.Status.FOUND).location(URI.create(location.toString())).build();
    }

    private Response tokenError(String error, String description, int status) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        return Response.status(status)
                .entity(body)
                .header("Cache-Control", "no-store")
                .build();
    }

    private static String asString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private static List<String> asStringList(Map<String, Object> map, String key) {
        if (map == null) {
            return List.of();
        }
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> String.valueOf(item).trim())
                .toList();
    }

    private static String labelForProvider(String provider) {
        return switch (provider) {
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            case "github" -> "GitHub";
            default -> provider;
        };
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
