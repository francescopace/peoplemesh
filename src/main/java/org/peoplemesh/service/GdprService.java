package org.peoplemesh.service;

import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GdprService {

    @Inject
    EncryptionService encryption;

    @Inject
    AuditService audit;

    @Inject
    ConsentService consentService;

    @Inject
    ProfileService profileService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager em;

    /**
     * GDPR Art. 15 + Art. 20: full data export in JSON.
     */
    public String exportAllData(UUID userId) {
        ObjectNode root = objectMapper.createObjectNode();

        UserIdentity identity = UserIdentity.findActiveById(userId).orElse(null);
        if (identity != null) {
            ObjectNode identityNode = objectMapper.createObjectNode();
            identityNode.put("id", identity.id.toString());
            identityNode.put("oauth_provider", identity.oauthProvider);
            identityNode.put("created_at", identity.createdAt.toString());
            identityNode.put("email_encrypted", identity.emailEncrypted != null ? "[encrypted]" : null);
            root.set("identity", identityNode);
        }

        profileService.getProfile(userId).ifPresent(schema -> {
            root.set("profile", objectMapper.valueToTree(schema));
        });

        List<ProfileConsent> consents = ProfileConsent.findActiveByUserId(userId);
        root.set("consents", objectMapper.valueToTree(
                consents.stream().map(c -> {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("scope", c.scope);
                    n.put("granted_at", c.grantedAt.toString());
                    n.put("policy_version", c.policyVersion);
                    n.put("active", c.isActive());
                    return n;
                }).toList()
        ));

        List<Connection> connections = Connection.findByUserId(userId);
        root.set("connections", objectMapper.valueToTree(
                connections.stream().map(c -> {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("connected_at", c.connectedAt.toString());
                    n.put("partner_id", (c.userAId.equals(userId) ? c.userBId : c.userAId).toString());
                    return n;
                }).toList()
        ));

        root.put("exported_at", Instant.now().toString());

        audit.log(userId, "DATA_EXPORTED", "gdpr_export");

        return root.toPrettyString();
    }

    /**
     * GDPR Art. 17: right to erasure. Removes all user data from related tables,
     * soft-deletes identity and profile, then purges the encryption key.
     */
    @Transactional
    public void deleteAllData(UUID userId) {
        cleanupUserRelations(userId);

        profileService.deleteProfile(userId);

        UserIdentity identity = UserIdentity.findActiveById(userId).orElse(null);
        if (identity != null) {
            identity.deletedAt = Instant.now();
            identity.persist();
        }

        consentService.revokeAllConsents(userId);
        encryption.deleteKey(userId);

        audit.log(userId, "ACCOUNT_DELETED", "gdpr_delete");
    }

    /**
     * GDPR Art. 18: restrict processing. Profile stays but is excluded from matching.
     */
    @Transactional
    public void restrictProcessing(UUID userId) {
        UserProfile.findActiveByUserId(userId).ifPresent(p -> {
            p.searchable = false;
            p.persist();
        });
        audit.log(userId, "PROCESSING_RESTRICTED", "gdpr_restrict");
    }

    public PrivacyDashboard getPrivacyDashboard(UUID userId) {
        int pendingRequests = ConnectionRequest.findPendingForUser(userId).size();
        UserProfile profile = UserProfile.findActiveByUserId(userId).orElse(null);
        Instant lastUpdate = profile != null ? profile.updatedAt : null;
        boolean searchable = profile != null && profile.searchable;
        int activeConsents = ProfileConsent.findActiveByUserId(userId).size();

        return new PrivacyDashboard(0, pendingRequests, lastUpdate, searchable, activeConsents);
    }

    /**
     * Hard-delete all soft-deleted data older than the given threshold.
     * Cleans up related tables first to avoid FK violations.
     */
    @Transactional
    public int purgeDeletedData(Instant threshold) {
        int count = 0;

        List<UUID> profileUserIds = em.createQuery(
                "SELECT p.userId FROM UserProfile p WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :threshold",
                UUID.class)
                .setParameter("threshold", threshold)
                .getResultList();

        List<UUID> identityIds = em.createQuery(
                "SELECT u.id FROM UserIdentity u WHERE u.deletedAt IS NOT NULL AND u.deletedAt < :threshold",
                UUID.class)
                .setParameter("threshold", threshold)
                .getResultList();

        for (UUID userId : profileUserIds) {
            cleanupUserRelations(userId);
        }
        for (UUID userId : identityIds) {
            if (!profileUserIds.contains(userId)) {
                cleanupUserRelations(userId);
            }
        }

        count += em.createQuery(
                "DELETE FROM UserProfile p WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :threshold")
                .setParameter("threshold", threshold)
                .executeUpdate();
        count += em.createQuery(
                "DELETE FROM UserIdentity u WHERE u.deletedAt IS NOT NULL AND u.deletedAt < :threshold")
                .setParameter("threshold", threshold)
                .executeUpdate();
        return count;
    }

    /**
     * Find inactive profiles for retention notification.
     * Uses explicit JPQL select to ensure we get userId, not the entity PK.
     */
    public List<UUID> findInactiveUserIds(Instant threshold) {
        return em.createQuery(
                "SELECT p.userId FROM UserProfile p WHERE p.deletedAt IS NULL AND p.lastActiveAt < :threshold",
                UUID.class)
                .setParameter("threshold", threshold)
                .getResultList();
    }

    /**
     * Removes connections, connection requests, blocklist entries, and consents
     * for a given user. Must be called before hard-deleting identity/profile.
     */
    private void cleanupUserRelations(UUID userId) {
        em.createQuery("DELETE FROM Connection c WHERE c.userAId = :uid OR c.userBId = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
        em.createQuery("DELETE FROM ConnectionRequest r WHERE r.fromUserId = :uid OR r.toUserId = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
        em.createQuery("DELETE FROM BlocklistEntry b WHERE b.blockerId = :uid OR b.blockedId = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
        em.createQuery("DELETE FROM ProfileConsent c WHERE c.userId = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
    }
}
