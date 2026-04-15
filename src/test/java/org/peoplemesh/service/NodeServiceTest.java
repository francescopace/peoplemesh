package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NodeServiceTest {

    private final NodeService nodeService = new NodeService();

    @Test
    void nodeToText_fullNode_containsAllFields() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.PROJECT;
        node.title = "Mesh title";
        node.description = "Mesh body";
        node.tags = List.of("a", "b");
        node.country = "FR";

        String text = nodeService.nodeToText(node);

        assertTrue(text.contains("Type: PROJECT"));
        assertTrue(text.contains("Title: Mesh title"));
        assertTrue(text.contains("Description: Mesh body"));
        assertTrue(text.contains("Tags: a, b"));
        assertTrue(text.contains("Country: FR"));
    }

    @Test
    void nodeToText_minimalNode_containsTypeAndTitle() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.EVENT;
        node.title = "Meetup";
        node.description = "Details";

        String text = nodeService.nodeToText(node);

        assertTrue(text.contains("Type: EVENT"));
        assertTrue(text.contains("Title: Meetup"));
        assertTrue(text.contains("Description: Details"));
    }

    @Test
    void nodeToText_withTags_containsTags() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.PROJECT;
        node.title = "Project";
        node.description = "Build it";
        node.tags = List.of("java", "quarkus");

        String text = nodeService.nodeToText(node);

        assertTrue(text.contains("Tags: java, quarkus"));
    }

    @Test
    void nodeToText_withCountry_containsCountry() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.JOB;
        node.title = "Role";
        node.description = "Join us";
        node.country = "IE";

        String text = nodeService.nodeToText(node);

        assertTrue(text.contains("Country: IE"));
    }

    @Test
    void toDto_mapsAllFields() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.PROJECT;
        node.title = "Project X";
        node.description = "Description";
        node.tags = List.of("tag1");
        node.country = "US";

        NodeDto dto = nodeService.toDto(node);

        assertEquals(node.id, dto.id());
        assertEquals(NodeType.PROJECT, dto.nodeType());
        assertEquals("Project X", dto.title());
        assertEquals("Description", dto.description());
        assertEquals(List.of("tag1"), dto.tags());
        assertEquals("US", dto.country());
    }
}
