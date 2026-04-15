package org.peoplemesh.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.service.SessionService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PmSessionAuthenticationMechanismTest {

    PmSessionAuthenticationMechanism mechanism = new PmSessionAuthenticationMechanism();

    @Mock RoutingContext routingContext;
    @Mock HttpServerRequest httpRequest;
    @Mock IdentityProviderManager identityProviderManager;

    @BeforeEach
    void setUp() {
        lenient().when(routingContext.request()).thenReturn(httpRequest);
    }

    @Test
    void authenticate_nonApiPath_returnsNull() {
        when(routingContext.normalizedPath()).thenReturn("/q/health");

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void authenticate_apiPath_noCookie_returnsNull() {
        when(routingContext.normalizedPath()).thenReturn("/api/v1/me");
        when(httpRequest.getCookie(SessionService.COOKIE_NAME)).thenReturn(null);

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void authenticate_apiPath_blankCookie_returnsNull() {
        when(routingContext.normalizedPath()).thenReturn("/api/v1/me");
        Cookie cookie = mock(Cookie.class);
        when(cookie.getValue()).thenReturn("  ");
        when(httpRequest.getCookie(SessionService.COOKIE_NAME)).thenReturn(cookie);

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void authenticate_apiPath_validCookie_delegatesToIdentityProvider() {
        when(routingContext.normalizedPath()).thenReturn("/api/v1/me");
        Cookie cookie = mock(Cookie.class);
        when(cookie.getValue()).thenReturn("signed-cookie-value");
        when(httpRequest.getCookie(SessionService.COOKIE_NAME)).thenReturn(cookie);

        SecurityIdentity expectedIdentity = mock(SecurityIdentity.class);
        when(identityProviderManager.authenticate(any(PmSessionAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(expectedIdentity));

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertSame(expectedIdentity, result);
    }

    @Test
    void authenticate_mcpPath_validCookie_delegatesToIdentityProvider() {
        when(routingContext.normalizedPath()).thenReturn("/mcp/tools");
        Cookie cookie = mock(Cookie.class);
        when(cookie.getValue()).thenReturn("cookie");
        when(httpRequest.getCookie(SessionService.COOKIE_NAME)).thenReturn(cookie);

        SecurityIdentity expectedIdentity = mock(SecurityIdentity.class);
        when(identityProviderManager.authenticate(any(PmSessionAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(expectedIdentity));

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertSame(expectedIdentity, result);
    }

    @Test
    void authenticate_nullPath_returnsNull() {
        when(routingContext.normalizedPath()).thenReturn(null);

        SecurityIdentity result = mechanism.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void getChallenge_returnsNullOptional() {
        assertNull(mechanism.getChallenge(routingContext).await().indefinitely());
    }

    @Test
    void getCredentialTypes_containsPmSessionRequest() {
        Set<Class<? extends AuthenticationRequest>> types = mechanism.getCredentialTypes();
        assertTrue(types.contains(PmSessionAuthenticationRequest.class));
        assertEquals(1, types.size());
    }

    @Test
    void getCredentialTransport_returnsCookieType() {
        HttpCredentialTransport transport = mechanism.getCredentialTransport(routingContext)
                .await().indefinitely();
        assertEquals(HttpCredentialTransport.Type.COOKIE, transport.getTransportType());
    }
}
