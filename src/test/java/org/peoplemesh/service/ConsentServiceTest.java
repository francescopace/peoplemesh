package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNodeConsent;
import org.peoplemesh.repository.MeshNodeConsentRepository;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.util.HmacSigner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    MeshNodeConsentRepository meshNodeConsentRepository;

    @Mock
    AuditService auditService;

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
    void validateAndConsume_tokenWithoutSeparator_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> consentService.validateAndConsume("not-a-token", "profile_storage"));
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
    void validateAndConsume_validToken_returnsUserId() {
        UUID userId = UUID.randomUUID();
        String token = buildValidToken(userId, "profile_storage");

        UUID validated = consentService.validateAndConsume(token, "profile_storage");

        assertEquals(userId, validated);
        verify(tokenStore).tryConsume(eq(HashUtils.sha256(token)), any(Instant.class));
    }

    @Test
    void validateAndConsume_requiredScopeNull_acceptsTokenScope() {
        UUID userId = UUID.randomUUID();
        String token = buildValidToken(userId, "embedding_processing");

        UUID validated = consentService.validateAndConsume(token, null);

        assertEquals(userId, validated);
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

    @Test
    void hasActiveConsent_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(meshNodeConsentRepository.hasActiveConsent(userId, "professional_matching")).thenReturn(true);

        boolean result = consentService.hasActiveConsent(userId, "professional_matching");

        assertTrue(result);
    }

    @Test
    void getActiveScopes_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(meshNodeConsentRepository.findActiveScopes(userId))
                .thenReturn(List.of("professional_matching", "embedding_processing"));

        assertEquals(List.of("professional_matching", "embedding_processing"), consentService.getActiveScopes(userId));
    }

    @Test
    void getConsentView_returnsDefaultScopesAndActiveScopes() {
        UUID userId = UUID.randomUUID();
        when(meshNodeConsentRepository.findActiveScopes(userId)).thenReturn(List.of("professional_matching"));

        var view = consentService.getConsentView(userId);

        assertEquals(List.of("professional_matching"), view.get("active"));
        assertEquals(ConsentService.DEFAULT_CONSENT_SCOPES, view.get("scopes"));
    }

    @Test
    void grantConsent_validScope_persistsAndAudits() {
        UUID userId = UUID.randomUUID();

        consentService.grantConsent(
                userId,
                "professional_matching",
                ConsentService.DEFAULT_CONSENT_SCOPES,
                "ip-hash");

        verify(meshNodeConsentRepository).persist(any(MeshNodeConsent.class));
        verify(auditService).log(userId, "CONSENT_GRANTED", "privacy_consent");
    }

    @Test
    void grantConsent_invalidScope_throwsValidationBusinessException() {
        UUID userId = UUID.randomUUID();

        assertThrows(
                ValidationBusinessException.class,
                () -> consentService.grantConsent(
                        userId,
                        "unknown_scope",
                        ConsentService.DEFAULT_CONSENT_SCOPES,
                        "ip-hash"));
        verifyNoInteractions(auditService);
    }

    @Test
    void revokeConsent_validScope_revokesAndAudits() {
        UUID userId = UUID.randomUUID();

        consentService.revokeConsent(
                userId,
                "embedding_processing",
                ConsentService.DEFAULT_CONSENT_SCOPES);

        verify(meshNodeConsentRepository).revokeByNodeAndScope(userId, "embedding_processing");
        verify(auditService).log(userId, "CONSENT_REVOKED", "privacy_consent");
    }

    @Test
    void revokeConsent_invalidScope_throwsValidationBusinessException() {
        UUID userId = UUID.randomUUID();

        assertThrows(
                ValidationBusinessException.class,
                () -> consentService.revokeConsent(
                        userId,
                        "",
                        ConsentService.DEFAULT_CONSENT_SCOPES));
        verify(auditService, never()).log(any(UUID.class), anyString(), anyString());
    }

    @Test
    void validateConsentScope_validAndInvalidBranches() {
        assertDoesNotThrow(() -> consentService.validateConsentScope(
                "professional_matching",
                ConsentService.DEFAULT_CONSENT_SCOPES));

        assertThrows(
                ValidationBusinessException.class,
                () -> consentService.validateConsentScope(null, ConsentService.DEFAULT_CONSENT_SCOPES));
        assertThrows(
                ValidationBusinessException.class,
                () -> consentService.validateConsentScope("  ", ConsentService.DEFAULT_CONSENT_SCOPES));
    }
}
