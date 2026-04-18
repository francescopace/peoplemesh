package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.HmacSigner;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private final SessionService sessionService = new SessionService();

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.SessionConfig sessionConfig = mock(AppConfig.SessionConfig.class);
        when(config.session()).thenReturn(sessionConfig);
        when(sessionConfig.secret()).thenReturn("test-secret-with-at-least-32-chars-1234");

        AppConfig.OAuthConfig oauthConfig = mock(AppConfig.OAuthConfig.class);
        when(config.oauth()).thenReturn(oauthConfig);
        when(oauthConfig.stateSecret()).thenReturn("oauth-state-secret-32-chars-long!");

        Field configField = SessionService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(sessionService, config);
    }

    @Test
    void encodeDecode_roundTrip_preservesUserAndProvider() {
        UUID userId = UUID.randomUUID();
        String token = sessionService.encodeSession(userId, "github");

        Optional<SessionService.PmSession> decoded = sessionService.decodeSession(token);

        assertTrue(decoded.isPresent());
        assertEquals(userId, decoded.get().userId());
        assertEquals("github", decoded.get().provider());
    }

    @Test
    void decodeSession_tamperedSignature_returnsEmpty() {
        String token = sessionService.encodeSession(UUID.randomUUID(), "github");
        String tampered = token + "x";

        assertTrue(sessionService.decodeSession(tampered).isEmpty());
    }

    @Test
    void decodeSession_malformedToken_returnsEmpty() {
        assertTrue(sessionService.decodeSession(null).isEmpty());
        assertTrue(sessionService.decodeSession("").isEmpty());
        assertTrue(sessionService.decodeSession("no-dot-token").isEmpty());
        assertTrue(sessionService.decodeSession("..").isEmpty());
    }

    @Test
    void encodeSession_withDisplayName_preservesIt() {
        UUID userId = UUID.randomUUID();
        String token = sessionService.encodeSession(userId, "github", "Alice");

        Optional<SessionService.PmSession> decoded = sessionService.decodeSession(token);

        assertTrue(decoded.isPresent());
        assertEquals(userId, decoded.get().userId());
        assertEquals("github", decoded.get().provider());
        assertEquals("Alice", decoded.get().displayName());
    }

    @Test
    void sessionMaxAgeSeconds_isPositiveAndBounded() {
        int maxAge = sessionService.sessionMaxAgeSeconds();
        assertTrue(maxAge > 0);
        assertTrue(maxAge <= Integer.MAX_VALUE);
    }

    @Test
    void decodeSession_expiredToken_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        long expiredExp = Instant.now().getEpochSecond() - 86_400;
        String raw = userId + "|" + "github" + "|" + expiredExp;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        String secret = "test-secret-with-at-least-32-chars-1234";
        String token = encodedPayload + "." + HmacSigner.sign(encodedPayload, secret);

        assertTrue(sessionService.decodeSession(token).isEmpty());
    }

    @Test
    void signOAuthState_verifyOAuthState_roundTrip() {
        String token = sessionService.signOAuthState("github", "login");

        SessionService.OAuthStatePayload payload = sessionService.verifyOAuthState(token, "github");

        assertNotNull(payload);
        assertEquals("login", payload.intent());
    }

    @Test
    void verifyOAuthState_wrongProvider_returnsNull() {
        String token = sessionService.signOAuthState("github", "login");

        assertNull(sessionService.verifyOAuthState(token, "google"));
    }

    @Test
    void verifyOAuthState_tamperedToken_returnsNull() {
        String token = sessionService.signOAuthState("github", "login");

        assertNull(sessionService.verifyOAuthState(token + "x", "github"));
    }

    @Test
    void verifyOAuthState_nullToken_returnsNull() {
        assertNull(sessionService.verifyOAuthState(null, "github"));
    }

    @Test
    void verifyOAuthState_noDotsInToken_returnsNull() {
        assertNull(sessionService.verifyOAuthState("no-dot-token", "github"));
    }

    @Test
    void signOAuthState_importIntent_roundTrips() {
        String token = sessionService.signOAuthState("github", "import");

        SessionService.OAuthStatePayload payload = sessionService.verifyOAuthState(token, "github");

        assertNotNull(payload);
        assertEquals("import", payload.intent());
    }

    @Test
    void encodeSession_withNullDisplayName_decodesWithoutDisplayName() {
        UUID userId = UUID.randomUUID();
        String token = sessionService.encodeSession(userId, "google", null);

        Optional<SessionService.PmSession> decoded = sessionService.decodeSession(token);

        assertTrue(decoded.isPresent());
        assertEquals(userId, decoded.get().userId());
        assertEquals("google", decoded.get().provider());
        assertNull(decoded.get().displayName());
    }

}
