package org.peoplemesh.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.service.McpOAuthService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmBearerIdentityProviderTest {

    @Mock
    McpOAuthService mcpOAuthService;
    @Mock
    AuthenticationRequestContext context;

    @InjectMocks
    PmBearerIdentityProvider provider;

    @Test
    void getRequestType_returnsBearerRequestType() {
        assertEquals(PmBearerAuthenticationRequest.class, provider.getRequestType());
    }

    @Test
    void authenticate_missingToken_returnsAuthenticationFailure() {
        when(mcpOAuthService.resolveAccessToken("missing")).thenReturn(Optional.empty());

        AuthenticationFailedException failure = assertThrows(
                AuthenticationFailedException.class,
                () -> provider.authenticate(new PmBearerAuthenticationRequest("missing"), context)
                        .await().indefinitely()
        );

        assertTrue(failure.getMessage() == null || failure.getMessage().isBlank());
    }

    @Test
    void authenticate_validToken_setsPrincipalAndAttributes() {
        UUID userId = UUID.randomUUID();
        when(mcpOAuthService.resolveAccessToken("token-1")).thenReturn(Optional.of(
                new McpOAuthService.AccessTokenPrincipal(userId, "google", "Alice")
        ));

        SecurityIdentity identity = provider.authenticate(new PmBearerAuthenticationRequest("token-1"), context)
                .await().indefinitely();

        assertEquals("pm_oauth:" + userId, identity.getPrincipal().getName());
        assertEquals(userId, identity.getAttribute("pm.userId"));
        assertEquals("google", identity.getAttribute("pm.provider"));
        assertEquals("Alice", identity.getAttribute("pm.displayName"));
    }

    @Test
    void authenticate_blankDisplayName_skipsDisplayNameAttribute() {
        UUID userId = UUID.randomUUID();
        when(mcpOAuthService.resolveAccessToken("token-2")).thenReturn(Optional.of(
                new McpOAuthService.AccessTokenPrincipal(userId, "github", " ")
        ));

        SecurityIdentity identity = provider.authenticate(new PmBearerAuthenticationRequest("token-2"), context)
                .await().indefinitely();

        assertEquals("pm_oauth:" + userId, identity.getPrincipal().getName());
        assertEquals("github", identity.getAttribute("pm.provider"));
        assertFalse(identity.getAttributes().containsKey("pm.displayName"));
    }
}
