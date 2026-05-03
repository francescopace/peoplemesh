package org.peoplemesh.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpOAuthServiceTest {

    McpOAuthService service = new McpOAuthService();

    @Test
    void registerClient_defaultsToPublicAuthorizationCodeClient() {
        McpOAuthService.RegisteredClient client = service.registerClient(
                "  Demo Client  ",
                Arrays.asList(" https://app.example/callback ", " "),
                Arrays.asList(" authorization_code ", null),
                null
        );

        assertTrue(client.clientId().startsWith("pm-"));
        assertNull(client.clientSecret());
        assertEquals("Demo Client", client.clientName());
        assertEquals(List.of("https://app.example/callback"), client.redirectUris());
        assertEquals(List.of("authorization_code"), client.grantTypes());
        assertEquals("none", client.tokenEndpointAuthMethod());
        assertEquals(Optional.of(client), service.getClient(client.clientId()));
        assertTrue(service.getClient(" ").isEmpty());
    }

    @Test
    void registerClient_clientSecretPostKeepsSecretAndFallsBackOnDefaults() {
        McpOAuthService.RegisteredClient client = service.registerClient(
                " ",
                List.of(),
                Arrays.asList(" ", null),
                "client_secret_post"
        );

        assertNotNull(client.clientSecret());
        assertEquals("MCP Client", client.clientName());
        assertEquals(List.of(), client.redirectUris());
        assertEquals(List.of("authorization_code"), client.grantTypes());
        assertEquals("client_secret_post", client.tokenEndpointAuthMethod());
    }

    @Test
    void createAuthorizationRequest_rejectsUnknownClientAndRedirectMismatch() {
        assertTrue(service.createAuthorizationRequest(
                "missing",
                "https://app.example/callback",
                "state",
                "challenge",
                "openid").isEmpty());

        McpOAuthService.RegisteredClient client = service.registerClient(
                "Demo",
                List.of("https://allowed.example/callback"),
                List.of("authorization_code"),
                "none"
        );

        assertTrue(service.createAuthorizationRequest(
                client.clientId(),
                "https://other.example/callback",
                "state",
                "challenge",
                "openid").isEmpty());
    }

    @Test
    void authorizationCodeFlow_exchangesCodeAndResolvesToken() {
        McpOAuthService.RegisteredClient client = service.registerClient(
                "Demo",
                List.of("https://app.example/callback"),
                List.of("authorization_code"),
                "none"
        );

        McpOAuthService.AuthorizationRequest request = service.createAuthorizationRequest(
                client.clientId(),
                "https://app.example/callback",
                "opaque-state",
                null,
                "openid profile"
        ).orElseThrow();

        Optional<McpOAuthService.AuthorizationRequest> storedRequest = service.getAuthorizationRequest(request.requestId());
        assertTrue(storedRequest.isPresent());
        assertEquals("opaque-state", storedRequest.get().state());
        assertEquals("openid profile", storedRequest.get().scope());

        UUID userId = UUID.randomUUID();
        String redirect = service.completeAuthorization(
                request.requestId(),
                userId,
                "google",
                "Alice"
        ).orElseThrow();

        assertTrue(redirect.startsWith("https://app.example/callback?code="));
        assertTrue(redirect.contains("&state=opaque-state"));
        assertTrue(service.getAuthorizationRequest(request.requestId()).isEmpty());

        String code = redirect.substring(
                redirect.indexOf("code=") + 5,
                redirect.indexOf("&state=")
        );

        Map<String, Object> tokenBody = service.exchangeAuthorizationCode(
                client.clientId(),
                null,
                code,
                "https://app.example/callback",
                null
        ).orElseThrow();

        assertEquals("Bearer", tokenBody.get("token_type"));
        assertEquals(3600L, tokenBody.get("expires_in"));
        String accessToken = String.valueOf(tokenBody.get("access_token"));
        assertFalse(accessToken.isBlank());

        McpOAuthService.AccessTokenPrincipal principal = service.resolveAccessToken(accessToken).orElseThrow();
        assertEquals(userId, principal.userId());
        assertEquals("google", principal.provider());
        assertEquals("Alice", principal.displayName());
        assertTrue(service.exchangeAuthorizationCode(
                client.clientId(),
                null,
                code,
                "https://app.example/callback",
                null
        ).isEmpty());
    }

    @Test
    void exchangeAuthorizationCode_rejectsWrongVerifierAndWrongSecret() {
        McpOAuthService.RegisteredClient publicClient = service.registerClient(
                "Public",
                List.of("https://public.example/callback"),
                List.of("authorization_code"),
                "none"
        );
        McpOAuthService.AuthorizationRequest publicRequest = service.createAuthorizationRequest(
                publicClient.clientId(),
                "https://public.example/callback",
                null,
                "challenge",
                "openid"
        ).orElseThrow();
        String publicRedirect = service.completeAuthorization(
                publicRequest.requestId(),
                UUID.randomUUID(),
                "google",
                null
        ).orElseThrow();
        String publicCode = publicRedirect.substring(publicRedirect.indexOf("code=") + 5);

        assertTrue(service.exchangeAuthorizationCode(
                publicClient.clientId(),
                null,
                publicCode,
                "https://public.example/callback",
                ""
        ).isEmpty());

        McpOAuthService.RegisteredClient confidentialClient = service.registerClient(
                "Confidential",
                List.of("https://secure.example/callback"),
                List.of("authorization_code"),
                "client_secret_post"
        );
        McpOAuthService.AuthorizationRequest confidentialRequest = service.createAuthorizationRequest(
                confidentialClient.clientId(),
                "https://secure.example/callback",
                null,
                null,
                "openid"
        ).orElseThrow();
        String confidentialRedirect = service.completeAuthorization(
                confidentialRequest.requestId(),
                UUID.randomUUID(),
                "github",
                "Bob"
        ).orElseThrow();
        String confidentialCode = confidentialRedirect.substring(confidentialRedirect.indexOf("code=") + 5);

        assertTrue(service.exchangeAuthorizationCode(
                confidentialClient.clientId(),
                "wrong-secret",
                confidentialCode,
                "https://secure.example/callback",
                null
        ).isEmpty());
        assertTrue(service.resolveAccessToken("missing-token").isEmpty());
        assertTrue(service.completeAuthorization("missing-request", UUID.randomUUID(), "github", "Bob").isEmpty());
    }
}
