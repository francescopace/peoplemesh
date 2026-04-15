package org.peoplemesh.service;

import org.peoplemesh.util.HashUtils;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.*;
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

    @Inject
    AuditService audit;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ProfileService profileService;

    @Inject
    EntityManager em;

    /**
     * GDPR Art. 15 + Art. 20: full data export in JSON.
     */
    public String exportAllData(UUID userId) {
        LOG.infof("action=exportAllData userId=%s", userId);
        ObjectNode root = objectMapper.createObjectNode();

        MeshNode userNode = MeshNode.findPublishedUserNode(userId).orElse(null);

        List<UserIdentity> identities = UserIdentity.findByNodeId(userId);
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

        List<MeshNodeConsent> consents = MeshNodeConsent.findActiveByNodeId(userId);
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

        List<MeshNode> nodes = MeshNode.findByOwner(userId);
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

        em.createQuery("DELETE FROM MeshNode n WHERE n.createdBy = :uid AND n.nodeType != :userType")
                .setParameter("uid", userId)
                .setParameter("userType", NodeType.USER)
                .executeUpdate();

        em.createQuery("DELETE FROM MeshNodeConsent c WHERE c.nodeId = :uid")
                .setParameter("uid", userId).executeUpdate();

        em.createQuery("DELETE FROM MeshNode n WHERE n.id = :uid AND n.nodeType = :userType")
                .setParameter("uid", userId)
                .setParameter("userType", NodeType.USER)
                .executeUpdate();
    }

    public PrivacyDashboard getPrivacyDashboard(UUID userId) {
        MeshNode node = MeshNode.findPublishedUserNode(userId).orElse(null);
        Instant lastUpdate = node != null ? node.updatedAt : null;
        boolean searchable = node != null && node.searchable;
        List<MeshNodeConsent> activeConsentList = MeshNodeConsent.findActiveByNodeId(userId);
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
        return em.createNativeQuery(
                "SELECT mn.id FROM mesh.mesh_node mn " +
                "WHERE mn.node_type = 'USER' " +
                "AND NOT EXISTS (SELECT 1 FROM identity.user_identity ui " +
                "WHERE ui.node_id = mn.id AND ui.last_active_at >= :threshold)",
                UUID.class)
                .setParameter("threshold", threshold)
                .getResultList();
    }
}
