package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobServiceTest {

    private JobService jobService;
    private Method jobNodeToText;
    private Method isClosedStatusMethod;

    @BeforeEach
    void setUp() throws Exception {
        jobService = new JobService();
        jobNodeToText = JobService.class.getDeclaredMethod("jobNodeToText", MeshNode.class);
        jobNodeToText.setAccessible(true);
        isClosedStatusMethod = JobService.class.getDeclaredMethod("isClosedStatus", String.class);
        isClosedStatusMethod.setAccessible(true);
    }

    private String invokeJobNodeToText(MeshNode node) throws Throwable {
        try {
            return (String) jobNodeToText.invoke(jobService, node);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private boolean invokeIsClosedStatus(String atsStatus) throws Throwable {
        try {
            return (boolean) isClosedStatusMethod.invoke(null, atsStatus);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static MeshNode jobNodeWithStructured(String title, String description, Map<String, Object> structured, List<String> tags) {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = title;
        node.description = description;
        node.structuredData = structured;
        node.tags = tags;
        return node;
    }

    @Test
    void jobNodeToText_fullJob_containsAllFields() throws Throwable {
        Map<String, Object> sd = new HashMap<>();
        sd.put("requirements_text", "Java 21");
        sd.put("skills_required", List.of("Java", "Quarkus"));
        sd.put("work_mode", "HYBRID");
        sd.put("employment_type", "FREELANCE");
        MeshNode node = jobNodeWithStructured("Senior Dev", "Ship features", sd, List.of("Java", "Quarkus"));
        node.country = "DE";

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Title: Senior Dev"));
        assertTrue(text.contains("Description: Ship features"));
        assertTrue(text.contains("Requirements: Java 21"));
        assertTrue(text.contains("Required Skills: Java, Quarkus"));
        assertTrue(text.contains("Work Mode: HYBRID"));
        assertTrue(text.contains("Employment: FREELANCE"));
        assertTrue(text.contains("Country: DE"));
    }

    @Test
    void jobNodeToText_minimalJob_containsTitleAndDescription() throws Throwable {
        MeshNode node = jobNodeWithStructured("Only Title", "Only Desc", Map.of(), null);

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Title: Only Title"));
        assertTrue(text.contains("Description: Only Desc"));
    }

    @Test
    void jobNodeToText_withSkills_containsSkills() throws Throwable {
        MeshNode node = jobNodeWithStructured("Role", "Do work", Map.of(), List.of("Rust", "Kafka"));

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Required Skills: Rust, Kafka"));
    }

    @Test
    void jobNodeToText_withWorkMode_containsWorkMode() throws Throwable {
        Map<String, Object> sd = Map.of("work_mode", "REMOTE");
        MeshNode node = jobNodeWithStructured("Role", "Do work", sd, null);

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Work Mode: REMOTE"));
    }

    @Test
    void jobNodeToText_withEmploymentType_containsEmployment() throws Throwable {
        Map<String, Object> sd = Map.of("employment_type", "EMPLOYED");
        MeshNode node = jobNodeWithStructured("Role", "Do work", sd, null);

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Employment: EMPLOYED"));
    }

    @Test
    void jobNodeToText_withCountry_containsCountry() throws Throwable {
        MeshNode node = jobNodeWithStructured("Role", "Do work", Map.of(), null);
        node.country = "CA";

        String text = invokeJobNodeToText(node);

        assertTrue(text.contains("Country: CA"));
    }

    @Test
    void isClosedStatus_null_returnsFalse() throws Throwable {
        assertFalse(invokeIsClosedStatus(null));
    }

    @Test
    void isClosedStatus_closedVariants_returnsTrue() throws Throwable {
        for (String s : List.of("filled", "hired", "closed", "archived", "cancelled", "deleted")) {
            assertTrue(invokeIsClosedStatus(s), "Expected true for: " + s);
        }
    }

    @Test
    void isClosedStatus_openVariants_returnsFalse() throws Throwable {
        for (String s : List.of("published", "open", "active", "live", "draft", "paused", "whatever")) {
            assertFalse(invokeIsClosedStatus(s), "Expected false for: " + s);
        }
    }
}
