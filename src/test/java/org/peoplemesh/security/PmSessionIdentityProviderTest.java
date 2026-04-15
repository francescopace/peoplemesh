package org.peoplemesh.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.service.SessionService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PmSessionIdentityProviderTest {

    @Mock
    SessionService sessionService;

    @InjectMocks
    PmSessionIdentityProvider provider;

    @Mock
    AuthenticationRequestContext context;

    @Test
    void getRequestType_returnsPmSessionRequest() {
        assertEquals(PmSessionAuthenticationRequest.class, provider.getRequestType());
    }

    @Test
    void authenticate_validSession_returnsIdentityWithAttributes() {
        UUID userId = UUID.randomUUID();
        SessionService.PmSession session = new SessionService.PmSession(userId, "google", "Alice");
        when(sessionService.decodeSession("valid-cookie")).thenReturn(Optional.of(session));

        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest("valid-cookie");
        SecurityIdentity identity = provider.authenticate(request, context)
                .await().indefinitely();

        assertNotNull(identity);
        assertEquals("pm_session:" + userId, identity.getPrincipal().getName());
        assertEquals(userId, identity.getAttribute("pm.userId"));
        assertEquals("google", identity.getAttribute("pm.provider"));
        assertEquals("Alice", identity.getAttribute("pm.displayName"));
    }

    @Test
    void authenticate_validSessionNoDisplayName_identityLacksDisplayName() {
        UUID userId = UUID.randomUUID();
        SessionService.PmSession session = new SessionService.PmSession(userId, "github", null);
        when(sessionService.decodeSession("cookie")).thenReturn(Optional.of(session));

        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest("cookie");
        SecurityIdentity identity = provider.authenticate(request, context)
                .await().indefinitely();

        assertNotNull(identity);
        assertNull(identity.getAttribute("pm.displayName"));
    }

    @Test
    void authenticate_blankDisplayName_identityLacksDisplayName() {
        UUID userId = UUID.randomUUID();
        SessionService.PmSession session = new SessionService.PmSession(userId, "github", "  ");
        when(sessionService.decodeSession("cookie")).thenReturn(Optional.of(session));

        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest("cookie");
        SecurityIdentity identity = provider.authenticate(request, context)
                .await().indefinitely();

        assertNull(identity.getAttribute("pm.displayName"));
    }

    @Test
    void authenticate_invalidSession_throwsAuthenticationFailed() {
        when(sessionService.decodeSession("bad-cookie")).thenReturn(Optional.empty());

        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest("bad-cookie");

        assertThrows(AuthenticationFailedException.class,
                () -> provider.authenticate(request, context).await().indefinitely());
    }
}
