package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingTextBuilderTest {

    @Test
    void buildText_userNode_includesRolesAndSkills() {
        MeshNode node = userNode("Developer", List.of("Java", "Python"));
        node.id = null;

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Roles: Developer"));
        assertTrue(text.contains("Technical Skills: Java, Python"));
    }

    @Test
    void buildText_userNode_emptyAssessments_keepsOriginalTags() {
        UUID nodeId = UUID.randomUUID();
        MeshNode node = userNode("Dev", List.of("React"));
        node.id = nodeId;
        String text = EmbeddingTextBuilder.buildText(node);
        assertTrue(text.contains("React"));
    }

    @Test
    void buildText_userNode_includeSeniorityAndCountry() {
        MeshNode node = userNode("Dev", List.of());
        node.id = null;
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("seniority", "SENIOR");
        node.country = "IT";

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Seniority: SENIOR"));
        assertTrue(text.contains("Country: IT"));
    }

    @Test
    void buildText_userNode_includesLanguagesAndIndustries() {
        MeshNode node = userNode("Dev", List.of());
        node.id = null;
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("languages_spoken", List.of("English", "Italian"));
        node.structuredData.put("industries", "FinTech");

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Languages: English, Italian"));
        assertTrue(text.contains("Industries: FinTech"));
    }

    @Test
    void buildText_userNode_includesWorkModeAndEmployment() {
        MeshNode node = userNode("Dev", List.of());
        node.id = null;
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("work_mode", "REMOTE");
        node.structuredData.put("employment_type", "FULL_TIME");

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Work Mode: REMOTE"));
        assertTrue(text.contains("Employment: FULL_TIME"));
    }

    @Test
    void buildText_userNode_fieldOrderMatchesRuntimeContract_andExcludesPersonalSections() {
        MeshNode node = userNode("Backend Engineer", List.of("Java", "Spring"));
        node.id = null;
        node.country = "IT";
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("tools_and_tech", List.of("Docker", "Kubernetes"));
        node.structuredData.put("industries", "FinTech");
        node.structuredData.put("seniority", "SENIOR");
        node.structuredData.put("languages_spoken", List.of("English", "Italian"));
        node.structuredData.put("education", List.of("MSc Computer Science"));
        node.structuredData.put("topics_frequent", List.of("Distributed systems"));
        node.structuredData.put("learning_areas", List.of("Rust"));
        node.structuredData.put("project_types", List.of("Platform modernization"));
        node.structuredData.put("work_mode", "REMOTE");
        node.structuredData.put("employment_type", "EMPLOYED");
        node.structuredData.put("skills_soft", List.of("Communication"));
        node.structuredData.put("hobbies", List.of("Chess"));

        String text = EmbeddingTextBuilder.buildText(node);

        assertInOrder(
                text,
                "Roles: Backend Engineer",
                "Technical Skills: Java, Spring",
                "Tools: Docker, Kubernetes",
                "Industries: FinTech",
                "Seniority: SENIOR",
                "Languages: English, Italian",
                "Education: MSc Computer Science",
                "Country: IT",
                "Topics: Distributed systems",
                "Learning: Rust",
                "Projects: Platform modernization",
                "Work Mode: REMOTE",
                "Employment: EMPLOYED",
                "Soft Skills: Communication"
        );
        assertFalse(text.contains("Hobbies:"), "Runtime builder must not include personal hobby sections for USER nodes");
    }

    @Test
    void buildText_jobNode_includesTitleAndDescription() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.JOB;
        node.title = "Java Developer";
        node.description = "We need a Java expert";
        node.structuredData = new LinkedHashMap<>();
        node.tags = new ArrayList<>();

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Title: Java Developer"));
        assertTrue(text.contains("Description: We need a Java expert"));
    }

    @Test
    void buildText_genericNode_includesTypeAndTags() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.COMMUNITY;
        node.title = "Java Community";
        node.description = "A community for Java devs";
        node.tags = List.of("java", "community");
        node.country = "DE";
        node.structuredData = null;

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("Type: COMMUNITY"));
        assertTrue(text.contains("Title: Java Community"));
        assertTrue(text.contains("Tags: java, community"));
        assertTrue(text.contains("Country: DE"));
    }

    @Test
    void buildText_genericNode_includesStructuredData() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.EVENT;
        node.title = "Tech Conf";
        node.description = "";
        node.tags = null;
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("venue", "Online");
        node.structuredData.put("empty_field", "");
        node.structuredData.put("array_field", "[]");

        String text = EmbeddingTextBuilder.buildText(node);

        assertTrue(text.contains("venue: Online"));
        assertFalse(text.contains("empty field"));
        assertFalse(text.contains("array field"));
    }

    @Test
    void buildText_genericNode_blankDescription_excluded() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.PROJECT;
        node.title = "Proj";
        node.description = "   ";
        node.tags = null;
        node.structuredData = null;

        String text = EmbeddingTextBuilder.buildText(node);

        assertFalse(text.contains("Description"));
    }

    @Test
    void buildFromSchema_nullSchema_returnsEmptyString() {
        assertEquals("", EmbeddingTextBuilder.buildFromSchema(null));
    }

    @Test
    void buildFromSchema_fullSchema_includesAllMappedSections() {
        ProfileSchema.ProfessionalInfo professional = new ProfileSchema.ProfessionalInfo(
                List.of("Engineer", "Architect"),
                Seniority.SENIOR,
                List.of("FinTech", "HealthTech"),
                List.of("Java", "Kotlin"),
                List.of("Mentoring"),
                List.of("Docker", "Kubernetes"),
                List.of("English", "Italian"),
                WorkMode.REMOTE,
                EmploymentType.FREELANCE
        );
        ProfileSchema.InterestsInfo interests = new ProfileSchema.InterestsInfo(
                List.of("Distributed systems"),
                List.of("Rust"),
                List.of("Open source")
        );
        ProfileSchema.PersonalInfo personal = new ProfileSchema.PersonalInfo(
                List.of("Chess"),
                List.of("Cycling"),
                List.of("MSc Computer Science"),
                List.of("Climate"),
                List.of("Curious"),
                List.of("Jazz"),
                List.of("Sci-fi")
        );
        ProfileSchema.GeographyInfo geography = new ProfileSchema.GeographyInfo("IT", "Milan", "Europe/Rome");
        ProfileSchema schema = new ProfileSchema(
                "v1",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                professional,
                null,
                interests,
                personal,
                geography,
                null,
                null
        );

        String text = EmbeddingTextBuilder.buildFromSchema(schema);

        assertTrue(text.contains("Roles: Engineer, Architect"));
        assertTrue(text.contains("Seniority: SENIOR"));
        assertTrue(text.contains("Industries: FinTech, HealthTech"));
        assertTrue(text.contains("Technical Skills: Java, Kotlin"));
        assertTrue(text.contains("Soft Skills: Mentoring"));
        assertTrue(text.contains("Tools: Docker, Kubernetes"));
        assertTrue(text.contains("Languages: English, Italian"));
        assertTrue(text.contains("Work Mode: REMOTE"));
        assertTrue(text.contains("Employment: FREELANCE"));
        assertTrue(text.contains("Topics: Distributed systems"));
        assertTrue(text.contains("Learning: Rust"));
        assertTrue(text.contains("Projects: Open source"));
        assertTrue(text.contains("Hobbies: Chess"));
        assertTrue(text.contains("Sports: Cycling"));
        assertTrue(text.contains("Education: MSc Computer Science"));
        assertTrue(text.contains("Causes: Climate"));
        assertTrue(text.contains("Personality: Curious"));
        assertTrue(text.contains("Music: Jazz"));
        assertTrue(text.contains("Books: Sci-fi"));
        assertTrue(text.contains("Country: IT"));
    }

    @Test
    void buildFromSchema_partialSchema_skipsNullNestedRecords() {
        ProfileSchema.PersonalInfo personal = new ProfileSchema.PersonalInfo(
                List.of("Reading"),
                null,
                null,
                null,
                null,
                null,
                null
        );
        ProfileSchema schema = new ProfileSchema(
                "v1",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                personal,
                null,
                null,
                null
        );

        String text = EmbeddingTextBuilder.buildFromSchema(schema);

        assertTrue(text.contains("Hobbies: Reading"));
        assertFalse(text.contains("Roles:"));
        assertFalse(text.contains("Topics:"));
        assertFalse(text.contains("Country:"));
    }

    private static MeshNode userNode(String description, List<String> tags) {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.USER;
        node.title = "Test User";
        node.description = description;
        node.tags = new ArrayList<>(tags);
        node.structuredData = new LinkedHashMap<>();
        return node;
    }

    private static void assertInOrder(String text, String... expectedSections) {
        int currentIndex = -1;
        for (String section : expectedSections) {
            int idx = text.indexOf(section);
            assertTrue(idx >= 0, "Missing section: " + section);
            assertTrue(idx > currentIndex, "Section out of order: " + section);
            currentIndex = idx;
        }
    }
}
