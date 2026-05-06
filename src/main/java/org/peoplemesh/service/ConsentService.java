package org.peoplemesh.service;

import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNodeConsent;
import org.peoplemesh.repository.MeshNodeConsentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ConsentService {

    private static final String CURRENT_POLICY_VERSION = "1.0";
    public static final Set<String> DEFAULT_CONSENT_SCOPES = Set.of(
            "professional_matching",
            "embedding_processing");

    @Inject
    MeshNodeConsentRepository meshNodeConsentRepository;

    @Inject
    AuditService auditService;

    @Transactional
    public void recordConsent(UUID nodeId, String scope, String ipHash) {
        MeshNodeConsent consent = new MeshNodeConsent();
        consent.nodeId = nodeId;
        consent.scope = scope;
        consent.grantedAt = Instant.now();
        consent.ipHash = ipHash;
        consent.policyVersion = CURRENT_POLICY_VERSION;
        meshNodeConsentRepository.persist(consent);
    }

    private void revokeConsentRecord(UUID nodeId, String scope) {
        meshNodeConsentRepository.revokeByNodeAndScope(nodeId, scope);
    }

    public boolean hasActiveConsent(UUID nodeId, String scope) {
        return meshNodeConsentRepository.hasActiveConsent(nodeId, scope);
    }

    public java.util.List<String> getActiveScopes(UUID nodeId) {
        return meshNodeConsentRepository.findActiveScopes(nodeId);
    }

    public Map<String, Object> getConsentView(UUID userId) {
        return Map.of(
                "scopes", DEFAULT_CONSENT_SCOPES,
                "active", getActiveScopes(userId));
    }

    @Transactional
    public void grantConsent(UUID userId, String scope, Collection<String> allowedScopes, String clientIpHash) {
        validateConsentScope(scope, allowedScopes);
        recordConsent(userId, scope, clientIpHash);
        auditService.log(userId, "CONSENT_GRANTED", "privacy_consent");
    }

    @Transactional
    public void revokeConsent(UUID userId, String scope, Collection<String> allowedScopes) {
        validateConsentScope(scope, allowedScopes);
        revokeConsentRecord(userId, scope);
        auditService.log(userId, "CONSENT_REVOKED", "privacy_consent");
    }

    public void validateConsentScope(String scope, Collection<String> allowedScopes) {
        if (scope == null || scope.isBlank() || !allowedScopes.contains(scope)) {
            throw new ValidationBusinessException("Invalid consent scope: " + scope);
        }
    }

}
