package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultNodeAccessPolicyServiceTest {

    private final DefaultNodeAccessPolicyService service = new DefaultNodeAccessPolicyService();

    @Test
    void canReadNode_nullRequesterOrNode_returnsFalse() {
        assertFalse(service.canReadNode(null, new MeshNode()));
        assertFalse(service.canReadNode(UUID.randomUUID(), null));
    }

    @Test
    void canReadNode_selfUser_returnsTrue() {
        UUID userId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = userId;
        node.nodeType = NodeType.USER;
        node.searchable = false;

        assertTrue(service.canReadNode(userId, node));
    }

    @Test
    void canReadNode_publicNonUser_returnsTrue() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.searchable = true;

        assertTrue(service.canReadNode(UUID.randomUUID(), node));
    }

    @Test
    void canReadNode_otherUserPrivateProfile_returnsFalse() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.USER;
        node.searchable = false;

        assertFalse(service.canReadNode(UUID.randomUUID(), node));
    }
}
