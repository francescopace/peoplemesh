package org.peoplemesh.service;

import org.peoplemesh.util.HashUtils;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.model.*;
import org.peoplemesh.repository.AuditLogRepository;
import org.peoplemesh.repository.GdprRepository;
import org.peoplemesh.repository.MeshNodeConsentRepository;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GdprService {

    private static final Logger LOG = Logger.getLogger(GdprService.class);
    private static final int MAX_AUDIT_EXPORT_ROWS = 1000;

    @Inject
    AuditService audit;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ProfileService profileService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    @Inject
    MeshNodeConsentRepository meshNodeConsentRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    GdprRepository gdprRepository;

    @Inject
    EntityManager em;

    /**
     * GDPR Art. 15 + Art. 20: full data export in JSON.
     */
    public String exportAllData(UUID userId) {
        LOG.infof("action=exportAllData userId=%s", userId);
        ObjectNode root = objectMapper.createObjectNode();

        MeshNode userNode = findPublishedUserNode(userId);

        List<UserIdentity> identities = findUserIdentities(userId);
        if (!identities.isEmpty()) {
            var identityArray = objectMapper.createArrayNode();
            for (UserIdentity identity : identities) {
                ObjectNode identityNode = objectMapper.createObjectNode();
                identityNode.put("id", identity.id.toString());
                identityNode.put("oauth_provider", identity.oauthProvider);
                identityNode.put("can_create_job", identity.canCreateJob);
                identityNode.put("can_manage_skills", identity.canManageSkills);
                if (identity.lastActiveAt != null) {
                    identityNode.put("last_active_at", identity.lastActiveAt.toString());
                }
                identityArray.add(identityNode);
            }
            root.set("identities", identityArray);
        }

        List<ObjectNode> profileNodes = new ArrayList<>();
        if (userNode != null) {
            ObjectNode pub = objectMapper.createObjectNode();
            pub.put("id", userNode.id.toString());
            pub.put("external_id", userNode.externalId);
            pub.put("created_at", userNode.createdAt != null ? userNode.createdAt.toString() : null);
            pub.put("updated_at", userNode.updatedAt != null ? userNode.updatedAt.toString() : null);
            profileService.getProfile(userId)
                    .ifPresent(schema -> pub.set("data", objectMapper.valueToTree(schema)));
            profileNodes.add(pub);
        }
        root.set("profile", objectMapper.valueToTree(profileNodes));

        List<MeshNodeConsent> consents = findActiveConsents(userId);
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

        List<MeshNode> nodes = findOwnedNodes(userId);
        root.set("mesh_nodes", objectMapper.valueToTree(
                nodes.stream().map(n -> {
                    ObjectNode nd = objectMapper.createObjectNode();
                    nd.put("id", n.id.toString());
                    nd.put("title", n.title);
                    nd.put("description", n.description);
                    nd.put("node_type", n.nodeType.name());
                    nd.put("created_at", n.createdAt.toString());
                    return nd;
                }).toList()
        ));

        String userIdHash = HashUtils.sha256(userId.toString());
        List<AuditLogEntry> auditEntries = loadAuditEntries(userIdHash);
        root.set("audit_log", objectMapper.valueToTree(
                auditEntries.stream().map(a -> {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("action", a.action);
                    n.put("tool_name", a.toolName);
                    n.put("timestamp", a.timestamp.toString());
                    return n;
                }).toList()
        ));

        root.put("exported_at", Instant.now().toString());

        audit.log(userId, "DATA_EXPORTED", "gdpr_export");

        return root.toPrettyString();
    }

    List<AuditLogEntry> loadAuditEntries(String userIdHash) {
        if (auditLogRepository != null) {
            return auditLogRepository.findByUserHash(userIdHash, MAX_AUDIT_EXPORT_ROWS);
        }
        return AuditLogEntry.list("userIdHash", userIdHash);
    }

    /**
     * GDPR Art. 17: right to erasure.
     * Deleting the USER mesh_node cascades to user_identity and skill_assessment.
     * mesh_node_consent and non-USER nodes are deleted explicitly.
     */
    @Transactional
    public void deleteAllData(UUID userId) {
        LOG.infof("action=deleteAllData userId=%s", userId);
        audit.log(userId, "ACCOUNT_DELETED", "gdpr_delete");

        if (gdprRepository != null) {
            gdprRepository.deleteNonUserNodesByOwner(userId);
            gdprRepository.deleteConsentsByNodeId(userId);
            gdprRepository.deleteUserNode(userId);
            return;
        }
        MeshNode.delete("createdBy = ?1 and nodeType != ?2", userId, org.peoplemesh.domain.enums.NodeType.USER);
        MeshNodeConsent.delete("nodeId", userId);
        MeshNode.delete("id = ?1 and nodeType = ?2", userId, org.peoplemesh.domain.enums.NodeType.USER);
    }

    public PrivacyDashboard getPrivacyDashboard(UUID userId) {
        MeshNode node = findPublishedUserNode(userId);
        Instant lastUpdate = node != null ? node.updatedAt : null;
        boolean searchable = node != null && node.searchable;
        List<MeshNodeConsent> activeConsentList = findActiveConsents(userId);
        List<String> scopes = activeConsentList.stream().map(c -> c.scope).distinct().toList();

        return new PrivacyDashboard(lastUpdate, searchable,
                activeConsentList.size(), scopes);
    }

    @Transactional
    public int enforceRetention(int inactiveMonths) {
        Instant threshold = Instant.now().minus(inactiveMonths * 30L, java.time.temporal.ChronoUnit.DAYS);
        List<UUID> inactiveUsers = findInactiveUserIds(threshold);
        for (UUID userId : inactiveUsers) {
            deleteAllData(userId);
        }
        return inactiveUsers.size();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> findInactiveUserIds(Instant threshold) {
        if (gdprRepository != null) {
            return gdprRepository.findInactiveUserIds(threshold, 10_000);
        }
        if (em != null) {
            return em.createNativeQuery(
                            "SELECT mn.id FROM mesh.mesh_node mn " +
                                    "WHERE mn.node_type = 'USER' " +
                                    "AND NOT EXISTS (SELECT 1 FROM identity.user_identity ui " +
                                    "WHERE ui.node_id = mn.id AND ui.last_active_at >= :threshold)",
                            UUID.class)
                    .setParameter("threshold", threshold)
                    .getResultList();
        }
        return List.of();
    }

    private MeshNode findPublishedUserNode(UUID userId) {
        if (nodeRepository != null) {
            return nodeRepository.findPublishedUserNode(userId).orElse(null);
        }
        return MeshNode.findPublishedUserNode(userId).orElse(null);
    }

    private List<UserIdentity> findUserIdentities(UUID userId) {
        if (userIdentityRepository != null) {
            return userIdentityRepository.findByNodeId(userId);
        }
        return UserIdentity.findByNodeId(userId);
    }

    private List<MeshNodeConsent> findActiveConsents(UUID userId) {
        if (meshNodeConsentRepository != null) {
            return meshNodeConsentRepository.findActiveByNodeId(userId);
        }
        return MeshNodeConsent.findActiveByNodeId(userId);
    }

    private List<MeshNode> findOwnedNodes(UUID userId) {
        if (nodeRepository != null) {
            return nodeRepository.findByOwner(userId, 500);
        }
        return MeshNode.findByOwner(userId);
    }
}
