package org.peoplemesh.domain.dto;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultItemTest {

    @Test
    void profile_setsResultTypeAndProfileFields() {
        UUID id = UUID.randomUUID();
        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                0.85, 0.9, 0.5, 1.0, 0.3, 0.78,
                List.of("Java"), List.of("Go"), List.of("Rust"), List.of("SEMANTIC_SIMILARITY"));
        Map<String, Integer> levels = Map.of("Java", 4);

        SearchResultItem item = SearchResultItem.profile(
                id, 0.78, "Alice", "https://avatar.url",
                List.of("Engineer"), Seniority.SENIOR,
                List.of("Java", "Python"), List.of("Docker"),
                List.of("English"), "US", "NYC",
                WorkMode.REMOTE, EmploymentType.EMPLOYED,
                "alice-slack", "alice@example.com", "alice_tg", "+39123456789", "linkedin.com/in/alice",
                breakdown, levels);

        assertEquals(id, item.id());
        assertEquals("profile", item.resultType());
        assertEquals(0.78, item.score());
        assertEquals("Alice", item.displayName());
        assertEquals("https://avatar.url", item.avatarUrl());
        assertEquals(List.of("Engineer"), item.roles());
        assertEquals(Seniority.SENIOR, item.seniority());
        assertEquals(List.of("Java", "Python"), item.skillsTechnical());
        assertEquals(List.of("Docker"), item.toolsAndTech());
        assertEquals(List.of("English"), item.languagesSpoken());
        assertEquals("US", item.country());
        assertEquals("NYC", item.city());
        assertEquals(WorkMode.REMOTE, item.workMode());
        assertEquals(EmploymentType.EMPLOYED, item.employmentType());
        assertEquals("alice-slack", item.slackHandle());
        assertEquals("alice@example.com", item.email());
        assertEquals("alice_tg", item.telegramHandle());
        assertEquals("+39123456789", item.mobilePhone());
        assertEquals("linkedin.com/in/alice", item.linkedinUrl());
        assertNull(item.nodeType());
        assertNull(item.title());
        assertNull(item.description());
        assertNull(item.tags());
        assertEquals(breakdown, item.breakdown());
        assertEquals(levels, item.skillLevels());
    }

    @Test
    void node_setsResultTypeAndNodeFields() {
        UUID id = UUID.randomUUID();
        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                0.7, 0.5, 0, 0, 0, 0.65,
                List.of("Docker"), List.of(), List.of(), List.of("TAG_MATCH"));

        SearchResultItem item = SearchResultItem.node(
                id, 0.65, NodeType.PROJECT, "My Project",
                "A cool project", List.of("java", "quarkus"), "DE",
                breakdown);

        assertEquals(id, item.id());
        assertEquals("node", item.resultType());
        assertEquals(0.65, item.score());
        assertEquals(NodeType.PROJECT, item.nodeType());
        assertEquals("My Project", item.title());
        assertEquals("A cool project", item.description());
        assertEquals(List.of("java", "quarkus"), item.tags());
        assertEquals("DE", item.country());
        assertNull(item.displayName());
        assertNull(item.avatarUrl());
        assertNull(item.roles());
        assertNull(item.seniority());
        assertNull(item.skillsTechnical());
        assertNull(item.toolsAndTech());
        assertNull(item.languagesSpoken());
        assertNull(item.city());
        assertNull(item.workMode());
        assertNull(item.employmentType());
        assertNull(item.slackHandle());
        assertNull(item.email());
        assertNull(item.telegramHandle());
        assertNull(item.mobilePhone());
        assertNull(item.linkedinUrl());
        assertNull(item.skillLevels());
    }

    @Test
    void profile_withNullOptionalFields_setsNulls() {
        UUID id = UUID.randomUUID();
        SearchResultItem item = SearchResultItem.profile(
                id, 0.5, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertEquals("profile", item.resultType());
        assertNull(item.displayName());
        assertNull(item.roles());
        assertNull(item.breakdown());
    }

    @Test
    void node_withMinimalFields() {
        UUID id = UUID.randomUUID();
        SearchResultItem item = SearchResultItem.node(
                id, 0.3, NodeType.COMMUNITY, "Group", null, null, null, null);

        assertEquals("node", item.resultType());
        assertEquals(NodeType.COMMUNITY, item.nodeType());
        assertEquals("Group", item.title());
        assertNull(item.description());
        assertNull(item.tags());
    }
}
