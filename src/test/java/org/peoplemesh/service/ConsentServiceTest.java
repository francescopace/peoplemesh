package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.util.HmacSigner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final String SECRET = "test-secret-32-chars-min-1234567";

    @Mock
    AppConfig config;

    @Mock
    ConsentTokenStore tokenStore;

    @InjectMocks
    ConsentService consentService;

    AppConfig.ConsentTokenConfig consentTokenConfig;

    @BeforeEach
    void setUp() {
        consentTokenConfig = mock(AppConfig.ConsentTokenConfig.class);
        lenient().when(config.consentToken()).thenReturn(consentTokenConfig);
        lenient().when(consentTokenConfig.secret()).thenReturn(SECRET);
        lenient().when(consentTokenConfig.ttlSeconds()).thenReturn(300);
        lenient().when(tokenStore.tryConsume(anyString(), any(Instant.class))).thenReturn(true);
    }

    @Test
    void validateAndConsume_nullToken_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> consentService.validateAndConsume(null, "profile_storage"));
    }

    @Test
    void validateAndConsume_payloadWithFewerThanThreePipeFields_throwsIllegalArgumentException() {
        String badPayload = UUID.randomUUID() + "|only-two-parts";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(badPayload.getBytes(StandardCharsets.UTF_8));
        String token = encoded + "." + HmacSigner.sign(encoded, SECRET);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> consentService.validateAndConsume(token, "profile_storage"));
        assertEquals("Malformed consent token payload", ex.getMessage());
    }

    @Test
    void validateAndConsume_tamperedSignature_throwsSecurityException() {
        String tampered = buildValidToken(UUID.randomUUID(), "profile_storage");
        String tamperedSig = tampered.substring(0, tampered.lastIndexOf('.')) + ".deadbeef";

        assertThrows(SecurityException.class, () -> consentService.validateAndConsume(tamperedSig, "profile_storage"));
    }

    @Test
    void validateAndConsume_expiredToken_throwsSecurityException() {
        when(consentTokenConfig.ttlSeconds()).thenReturn(0);
        UUID userId = UUID.randomUUID();
        String scope = "profile_storage";
        long oldTs = Instant.now().getEpochSecond() - 60;
        String payload = userId + "|" + oldTs + "|" + scope;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = encoded + "." + HmacSigner.sign(encoded, SECRET);

        assertThrows(SecurityException.class, () -> consentService.validateAndConsume(token, scope));
    }

    @Test
    void validateAndConsume_wrongScope_throwsSecurityException() {
        String token = buildValidToken(UUID.randomUUID(), "profile_storage");

        assertThrows(SecurityException.class, () -> consentService.validateAndConsume(token, "other_scope"));
    }

    @Test
    void validateAndConsume_alreadyUsed_throwsSecurityException() {
        when(tokenStore.tryConsume(anyString(), any(Instant.class))).thenReturn(false);
        String token = buildValidToken(UUID.randomUUID(), "profile_storage");

        assertThrows(SecurityException.class, () -> consentService.validateAndConsume(token, "profile_storage"));
    }

    @Test
    void releaseToken_delegatesToStore() {
        String token = buildValidToken(UUID.randomUUID(), "profile_storage");

        consentService.releaseToken(token);

        verify(tokenStore).release(HashUtils.sha256(token));
    }

    private String buildValidToken(UUID userId, String scope) {
        long now = Instant.now().getEpochSecond();
        String payload = userId.toString() + "|" + now + "|" + scope;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = HmacSigner.sign(encodedPayload, SECRET);
        return encodedPayload + "." + signature;
    }

    @Test
    void releaseToken_nullToken_doesNothing() {
        consentService.releaseToken(null);

        verify(tokenStore, never()).release(anyString());
    }
}
