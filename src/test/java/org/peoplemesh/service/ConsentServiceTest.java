package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNodeConsent;
import org.peoplemesh.repository.MeshNodeConsentRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    MeshNodeConsentRepository meshNodeConsentRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    ConsentService consentService;

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
