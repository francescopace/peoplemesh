package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodePayload;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeServiceFlowTest {

    @Test
    void createNode_appliesPayloadGeneratesEmbeddingAndAudits() {
        TestableNodeService service = new TestableNodeService();
        UUID owner = UUID.randomUUID();
        NodePayload payload = new NodePayload(
                NodeType.PROJECT,
                "  My Project  ",
                "  Build something  ",
                List.of("java", "quarkus"),
                Map.of("k", "v"),
                "IT"
        );

        NodeDto dto = service.createNode(owner, payload);

        assertNotNull(dto.id());
        assertEquals(NodeType.PROJECT, dto.nodeType());
        assertEquals("My Project", dto.title());
        assertEquals("Build something", dto.description());
        assertEquals(List.of("java", "quarkus"), dto.tags());
        assertEquals("IT", dto.country());
        assertEquals(owner, service.lastPersisted.createdBy);
        assertEquals("NODE_CREATED", service.lastAuditAction);
        assertNotNull(service.lastPersisted.embedding);
    }

    @Test
    void updateNode_existingNode_updatesAndAudits() {
        TestableNodeService service = new TestableNodeService();
        UUID owner = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        MeshNode existing = service.newPersistedNode(nodeId, owner, NodeType.PROJECT, "Old", "Old desc");
        service.byIdAndOwner = Optional.of(existing);

        NodePayload payload = new NodePayload(
                NodeType.EVENT,
                " New Title ",
                " New Desc ",
                List.of("event"),
                Map.of("a", 1),
                "DE"
        );

        Optional<NodeDto> updated = service.updateNode(owner, nodeId, payload);

        assertTrue(updated.isPresent());
        assertEquals(NodeType.EVENT, updated.get().nodeType());
        assertEquals("New Title", updated.get().title());
        assertEquals("New Desc", updated.get().description());
        assertEquals("NODE_UPDATED", service.lastAuditAction);
        assertEquals("DE", existing.country);
        assertNotNull(existing.embedding);
    }

    @Test
    void updateNode_missingNode_returnsEmpty() {
        TestableNodeService service = new TestableNodeService();
        service.byIdAndOwner = Optional.empty();

        Optional<NodeDto> out = service.updateNode(UUID.randomUUID(), UUID.randomUUID(),
                new NodePayload(NodeType.PROJECT, "t", "d", null, null, null));

        assertTrue(out.isEmpty());
        assertEquals(0, service.auditCount);
    }

    @Test
    void getAndListMethods_delegateToStores() {
        TestableNodeService service = new TestableNodeService();
        UUID owner = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        MeshNode n1 = service.newPersistedNode(nodeId, owner, NodeType.PROJECT, "P", "Desc");
        MeshNode n2 = service.newPersistedNode(UUID.randomUUID(), owner, NodeType.JOB, "J", "Role");
        service.byId = Optional.of(n1);
        service.byOwner = List.of(n1, n2);
        service.byOwnerAndType = List.of(n2);
        service.byIdAndOwner = Optional.of(n1);

        assertTrue(service.getNode(nodeId).isPresent());
        assertTrue(service.getNodeForCreator(nodeId, owner).isPresent());
        assertEquals(2, service.listByCreator(owner).size());
        assertEquals(1, service.listByCreatorAndType(owner, NodeType.JOB).size());
        service.byId = Optional.empty();
        assertFalse(service.getNode(UUID.randomUUID()).isPresent());
    }

    private static final class TestableNodeService extends NodeService {
        Optional<MeshNode> byId = Optional.empty();
        Optional<MeshNode> byIdAndOwner = Optional.empty();
        List<MeshNode> byOwner = List.of();
        List<MeshNode> byOwnerAndType = List.of();

        MeshNode lastPersisted;
        String lastAuditAction;
        int auditCount;

        MeshNode newPersistedNode(UUID id, UUID owner, NodeType type, String title, String description) {
            MeshNode n = new MeshNode();
            n.id = id;
            n.createdBy = owner;
            n.nodeType = type;
            n.title = title;
            n.description = description;
            n.tags = new ArrayList<>();
            n.structuredData = new LinkedHashMap<>();
            n.createdAt = Instant.now();
            n.updatedAt = Instant.now();
            return n;
        }

        @Override
        Optional<MeshNode> findNodeById(UUID nodeId) {
            return byId;
        }

        @Override
        Optional<MeshNode> findNodeByIdAndOwner(UUID nodeId, UUID ownerUserId) {
            return byIdAndOwner;
        }

        @Override
        List<MeshNode> findNodesByOwner(UUID ownerUserId) {
            return byOwner;
        }

        @Override
        List<MeshNode> findNodesByOwnerAndType(UUID ownerUserId, NodeType type) {
            return byOwnerAndType;
        }

        @Override
        float[] generateEmbedding(String text) {
            return new float[]{0.1f, 0.2f, 0.3f};
        }

        @Override
        void persistNode(MeshNode node) {
            if (node.id == null) {
                node.id = UUID.randomUUID();
            }
            node.updatedAt = Instant.now();
            if (node.createdAt == null) {
                node.createdAt = node.updatedAt;
            }
            lastPersisted = node;
            byId = Optional.of(node);
        }

        @Override
        void logAudit(UUID userId, String action, String toolName) {
            lastAuditAction = action;
            auditCount++;
        }
    }
}
