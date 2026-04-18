package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.util.HmacSigner;
import org.peoplemesh.domain.model.MeshNodeConsent;
import org.peoplemesh.repository.MeshNodeConsentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Validates HMAC-SHA256 signed consent tokens and manages consent records.
 * Tokens are single-use (tracked via {@link ConsentTokenStore}) with a configurable TTL.
 */
@ApplicationScoped
public class ConsentService {

    private static final Logger LOG = Logger.getLogger(ConsentService.class);
    private static final String CURRENT_POLICY_VERSION = "1.0";
    public static final Set<String> DEFAULT_CONSENT_SCOPES = Set.of(
            "professional_matching",
            "embedding_processing");

    @Inject
    AppConfig config;

    @Inject
    ConsentTokenStore tokenStore;

    @Inject
    MeshNodeConsentRepository meshNodeConsentRepository;

    /**
     * Validates signature, TTL, scope, and single-use via {@link ConsentTokenStore}.
     * Returns the userId if valid, throws otherwise.
     *
     * @param requiredScope the scope this token must authorize (e.g. "profile_storage")
     */
    public UUID validateAndConsume(String token, String requiredScope) {
        if (token == null || !token.contains(".")) {
            throw new IllegalArgumentException("Invalid consent token format");
        }

        String[] parts = token.split("\\.", 2);
        String encodedPayload = parts[0];
        String signature = parts[1];

        if (!HmacSigner.verify(encodedPayload, signature, config.consentToken().secret())) {
            LOG.warnf("Consent token signature verification failed for scope=%s", requiredScope);
            throw new SecurityException("Consent token signature verification failed");
        }

        String payload = new String(
                Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|", 3);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Malformed consent token payload");
        }

        UUID userId = UUID.fromString(fields[0]);
        long timestamp = Long.parseLong(fields[1]);
        String tokenScope = fields[2];
        long now = Instant.now().getEpochSecond();

        if (now - timestamp > config.consentToken().ttlSeconds()) {
            throw new SecurityException("Consent token expired");
        }

        if (requiredScope != null && !requiredScope.equals(tokenScope)) {
            throw new SecurityException("Consent token scope mismatch: expected " +
                    requiredScope + " but token has " + tokenScope);
        }

        String tokenHash = HashUtils.sha256(token);
        Instant expiresAt = Instant.now()
                .plusSeconds(config.consentToken().ttlSeconds() * 2L);
        if (!tokenStore.tryConsume(tokenHash, expiresAt)) {
            throw new SecurityException("Consent token already used");
        }

        return userId;
    }

    /**
     * Marks a consumed token as unused so it can be retried
     * (called on rollback when profile submission fails after token consumption).
     */
    public void releaseToken(String token) {
        if (token == null) return;
        tokenStore.release(HashUtils.sha256(token));
    }

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

    @Transactional
    public void revokeConsent(UUID nodeId, String scope) {
        meshNodeConsentRepository.revokeByNodeAndScope(nodeId, scope);
    }

    public boolean hasActiveConsent(UUID nodeId, String scope) {
        return meshNodeConsentRepository.hasActiveConsent(nodeId, scope);
    }

    public java.util.List<String> getActiveScopes(UUID nodeId) {
        return meshNodeConsentRepository.findActiveScopes(nodeId);
    }

}
