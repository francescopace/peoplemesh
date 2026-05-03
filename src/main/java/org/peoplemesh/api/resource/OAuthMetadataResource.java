package org.peoplemesh.api.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthMetadataResource {

    @GET
    @Path("/oauth-protected-resource{path: (/mcp)?}")
    public Response oauthProtectedResource(@Context UriInfo uriInfo) {
        String origin = trimTrailingSlash(uriInfo.getBaseUri().toString());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", origin + "/mcp");
        metadata.put("authorization_servers", List.of(origin));
        metadata.put("bearer_methods_supported", List.of("header"));
        metadata.put("scopes_supported", List.of("openid", "profile"));
        return Response.ok(metadata).build();
    }

    @GET
    @Path("/oauth-authorization-server")
    public Response oauthAuthorizationServer(@Context UriInfo uriInfo) {
        String origin = trimTrailingSlash(uriInfo.getBaseUri().toString());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", origin);
        metadata.put("authorization_endpoint", origin + "/oauth/authorize");
        metadata.put("token_endpoint", origin + "/oauth/token");
        metadata.put("registration_endpoint", origin + "/oauth/register");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_post"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("scopes_supported", List.of("openid", "profile"));
        return Response.ok(metadata).build();
    }

    private static String trimTrailingSlash(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int end = raw.length();
        while (end > 1 && raw.charAt(end - 1) == '/') {
            end--;
        }
        return raw.substring(0, end);
    }
}
