package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.AuthProvidersDto;
import org.peoplemesh.service.McpOAuthService;
import org.peoplemesh.service.OAuthLoginService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthServerResourceTest {

    @Mock
    McpOAuthService mcpOAuthService;
    @Mock
    OAuthLoginService oAuthLoginService;
    @Mock
    UriInfo uriInfo;

    @InjectMocks
    OAuthServerResource resource;

    @Test
    void authorize_invalidResponseType_redirectsWithError() {
        Response response = resource.authorize(
                "token",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                null,
                "openid"
        );

        assertEquals(302, response.getStatus());
        assertEquals(
                "https://app.example/callback?error=unsupported_response_type&error_description=Only+code+is+supported&state=opaque-state",
                response.getLocation().toString()
        );
    }

    @Test
    void authorize_missingRedirectUri_returnsBadRequest() {
        Response response = resource.authorize("code", "client-1", " ", null, null, null, "openid");

        assertEquals(400, response.getStatus());
        assertEquals("redirect_uri is required", response.getEntity());
    }

    @Test
    void authorize_invalidPkceMethod_redirectsWithError() {
        Response response = resource.authorize(
                "code",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                "challenge",
                "plain",
                "openid"
        );

        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=invalid_request"));
        assertTrue(response.getLocation().toString().contains("state=opaque-state"));
    }

    @Test
    void authorize_invalidClient_redirectsWithError() {
        when(mcpOAuthService.createAuthorizationRequest(
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid"
        )).thenReturn(Optional.empty());

        Response response = resource.authorize(
                "code",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                null,
                "openid"
        );

        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=invalid_client"));
    }

    @Test
    void authorize_withoutLoginProviders_redirectsAccessDenied() {
        when(mcpOAuthService.createAuthorizationRequest(
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid"
        )).thenReturn(Optional.of(new McpOAuthService.AuthorizationRequest(
                "request-1",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid",
                Long.MAX_VALUE
        )));
        when(oAuthLoginService.providers()).thenReturn(new AuthProvidersDto(List.of(), List.of("github")));

        Response response = resource.authorize(
                "code",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                null,
                "openid"
        );

        assertEquals(302, response.getStatus());
        assertTrue(response.getLocation().toString().contains("error=access_denied"));
    }

    @Test
    void authorize_singleProvider_redirectsToLogin() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(mcpOAuthService.createAuthorizationRequest(
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                "challenge",
                "openid"
        )).thenReturn(Optional.of(new McpOAuthService.AuthorizationRequest(
                "request-1",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                "challenge",
                "openid",
                Long.MAX_VALUE
        )));
        when(oAuthLoginService.providers()).thenReturn(new AuthProvidersDto(List.of("google"), List.of()));

        Response response = resource.authorize(
                "code",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                "challenge",
                "S256",
                "openid"
        );

        assertEquals(302, response.getStatus());
        assertEquals(
                "https://api.peoplemesh.test/api/v1/auth/login/google?intent=mcp_oauth&ctx=request-1",
                response.getLocation().toString()
        );
    }

    @Test
    void authorize_multipleProviders_returnsChooserHtml() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(mcpOAuthService.createAuthorizationRequest(
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid"
        )).thenReturn(Optional.of(new McpOAuthService.AuthorizationRequest(
                "request-1",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid",
                Long.MAX_VALUE
        )));
        when(oAuthLoginService.providers()).thenReturn(new AuthProvidersDto(List.of("google", "custom<id>"), List.of()));

        Response response = resource.authorize(
                "code",
                "client-1",
                "https://app.example/callback",
                "opaque-state",
                null,
                null,
                "openid"
        );

        assertEquals(200, response.getStatus());
        assertEquals("text/html", response.getMediaType().toString());
        String html = assertInstanceOf(String.class, response.getEntity());
        assertTrue(html.contains("Authorize PeopleMesh"));
        assertTrue(html.contains("/api/v1/auth/login/google?intent=mcp_oauth&ctx=request-1"));
        assertTrue(html.contains("Continue with custom&lt;id&gt;"));
    }

    @Test
    void register_returnsCreatedClientPayload() {
        when(mcpOAuthService.registerClient(
                "CLI",
                List.of("https://app.example/callback"),
                List.of("authorization_code"),
                "client_secret_post"
        )).thenReturn(new McpOAuthService.RegisteredClient(
                "client-1",
                "secret-1",
                "CLI",
                List.of("https://app.example/callback"),
                List.of("authorization_code"),
                "client_secret_post"
        ));

        Response response = resource.register(Map.of(
                "client_name", "CLI",
                "redirect_uris", List.of("https://app.example/callback"),
                "grant_types", List.of("authorization_code"),
                "token_endpoint_auth_method", "client_secret_post"
        ));

        assertEquals(201, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("client-1", body.get("client_id"));
        assertEquals("secret-1", body.get("client_secret"));
        assertEquals(List.of("code"), body.get("response_types"));
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
    }

    @Test
    void register_whenRequestMissing_usesDefaults() {
        when(mcpOAuthService.registerClient("MCP Client", List.of(), List.of(), "none"))
                .thenReturn(new McpOAuthService.RegisteredClient(
                        "client-1",
                        null,
                        "MCP Client",
                        List.of(),
                        List.of("authorization_code"),
                        "none"
                ));

        Response response = resource.register(null);

        assertEquals(201, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = assertInstanceOf(Map.class, response.getEntity());
        assertTrue(!body.containsKey("client_secret"));
        verify(mcpOAuthService).registerClient("MCP Client", List.of(), List.of(), "none");
    }

    @Test
    void token_invalidGrantType_returnsErrorBody() {
        Response response = resource.token("refresh_token", "code", "cb", "client", null, "verifier");

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("unsupported_grant_type", body.get("error"));
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
    }

    @Test
    void token_invalidCode_returnsInvalidGrantError() {
        when(mcpOAuthService.exchangeAuthorizationCode("client", null, "code", "cb", "verifier"))
                .thenReturn(Optional.empty());

        Response response = resource.token("authorization_code", "code", "cb", "client", null, "verifier");

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("invalid_grant", body.get("error"));
    }

    @Test
    void token_success_returnsNoStoreTokenPayload() {
        when(mcpOAuthService.exchangeAuthorizationCode("client", "secret", "code", "cb", "verifier"))
                .thenReturn(Optional.of(Map.of(
                        "access_token", "token-1",
                        "token_type", "Bearer",
                        "expires_in", 3600L
                )));

        Response response = resource.token("authorization_code", "code", "cb", "client", "secret", "verifier");

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("token-1", body.get("access_token"));
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        assertEquals("no-cache", response.getHeaderString("Pragma"));
    }
}
