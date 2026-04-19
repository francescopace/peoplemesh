package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileSearchQueryBuilderTest {

    private final ProfileSearchQueryBuilder builder = new ProfileSearchQueryBuilder();

    @Test
    void buildFromUserNode_whenNull_returnsEmptyQuery() {
        SearchQuery query = builder.buildFromUserNode(null);

        assertEquals("search", query.embeddingText());
        assertEquals("unknown", query.seniority());
        assertEquals("all", query.resultScope());
    }

    @Test
    void buildFromUserNode_buildsRoleFirstEmbeddingText() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.USER;
        node.title = "Jane Doe";
        node.description = "Backend Engineer, Platform Lead";
        node.tags = List.of("Java", "Spring", "Kubernetes");
        node.structuredData = Map.of(
                "languages_spoken", List.of("English", "Italian"),
                "industries", List.of("Fintech"),
                "tools_and_tech", List.of("Docker", "PostgreSQL"),
                "topics_frequent", List.of("Microservices"),
                "learning_areas", List.of("Event Sourcing")
        );

        SearchQuery query = builder.buildFromUserNode(node);

        assertTrue(query.embeddingText().startsWith("roles: Backend Engineer, Platform Lead."));
        assertTrue(query.embeddingText().contains("skills: Java, Spring, Kubernetes, Docker, PostgreSQL"));
        assertTrue(query.embeddingText().contains("languages: English, Italian"));
        assertTrue(query.embeddingText().contains("industries: Fintech"));
        assertTrue(query.embeddingText().contains("topics:"));
    }
}

