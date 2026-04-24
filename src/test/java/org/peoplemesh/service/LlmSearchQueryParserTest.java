package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SearchQuery;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmSearchQueryParserTest {

    @Mock
    ChatModel chatModel;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    LlmSearchQueryParser parser;

    @Test
    void parse_nullQuery_returnsEmpty() {
        assertEquals(Optional.empty(), parser.parse(null));
    }

    @Test
    void parse_blankQuery_returnsEmpty() {
        assertEquals(Optional.empty(), parser.parse("   "));
    }

    @Test
    void parse_validLlmResponse_returnsParsed() {
        String json = """
                {
                  "must_have": {"skills": ["Java"], "languages": [], "industries": []},
                  "nice_to_have": {"skills": [], "industries": []},
                  "seniority": "senior",
                  "keywords": ["backend"],
                  "embedding_text": "Senior Java developer",
                  "result_scope": "people"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("looking for senior Java dev");

        assertTrue(result.isPresent());
        assertEquals("senior", result.get().seniority());
        assertEquals("people", result.get().resultScope());
    }

    @Test
    void parse_resultScopeAll_returnsParsed() {
        String json = """
                {
                  "must_have": {"skills": ["Java"], "languages": [], "industries": []},
                  "nice_to_have": {"skills": [], "industries": []},
                  "seniority": "unknown",
                  "keywords": ["java"],
                  "embedding_text": "Java and Kubernetes",
                  "result_scope": "all"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("tutti i risultati con Java e Kubernetes");

        assertTrue(result.isPresent());
        assertEquals("all", result.get().resultScope());
    }

    @Test
    void parse_extendedSchemaFields_returnsParsedWithMappedValues() {
        String json = """
                {
                  "must_have": {
                    "skills": ["Java", "Kubernetes"],
                    "skills_with_level": [{"name": "Kubernetes", "min_level": 3}],
                    "roles": ["developer"],
                    "languages": ["English"],
                    "location": ["Europe"],
                    "industries": ["technology"]
                  },
                  "nice_to_have": {
                    "skills": ["Spring Boot"],
                    "skills_with_level": [{"name": "Kafka", "min_level": 2}],
                    "industries": ["finance"],
                    "experience": ["community"]
                  },
                  "seniority": "mid",
                  "negative_filters": {
                    "seniority": "junior",
                    "skills": ["PHP"],
                    "location": ["Germany"]
                  },
                  "keywords": ["community", "events"],
                  "embedding_text": "Java Kubernetes developer in Europe",
                  "result_scope": "communities"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("community for java devs in Europe");

        assertTrue(result.isPresent());
        assertNotNull(result.get().mustHave());
        assertNotNull(result.get().niceToHave());
        assertNotNull(result.get().negativeFilters());
        assertEquals("developer", result.get().mustHave().roles().get(0));
        assertEquals("Europe", result.get().mustHave().location().get(0));
        assertEquals(3, result.get().mustHave().skillsWithLevel().get(0).minLevel());
        assertEquals(2, result.get().niceToHave().skillsWithLevel().get(0).minLevel());
        assertEquals("community", result.get().niceToHave().experience().get(0));
        assertEquals("junior", result.get().negativeFilters().seniority());
        assertEquals("communities", result.get().resultScope());
    }

    @Test
    void systemPrompt_includesExtendedSchemaAndTypeGuardrails() throws Exception {
        Field promptField = LlmSearchQueryParser.class.getDeclaredField("SYSTEM_PROMPT");
        promptField.setAccessible(true);
        String prompt = (String) promptField.get(null);

        assertTrue(prompt.contains("\"roles\""));
        assertTrue(prompt.contains("\"location\""));
        assertTrue(prompt.contains("\"negative_filters\""));
        assertTrue(prompt.contains("\"experience\""));
        assertTrue(prompt.contains("\"min_level\""));
        assertTrue(prompt.contains("\"result_scope\""));
        assertTrue(prompt.contains("languages = spoken human languages only"));
        assertTrue(prompt.contains("location = geographic places"));
        assertTrue(prompt.contains("industries = business sectors only"));
        assertTrue(prompt.contains("Open roles in cloud architecture"));
        assertTrue(prompt.contains("keep seniority as \"unknown\""));
        assertTrue(prompt.contains("all/tutti/everything/any type"));
    }

    @Test
    void parse_sparseModelOutput_normalizesNullCollectionsAndSeniority() {
        String json = """
                {
                  "must_have": {
                    "roles": ["cloud architect", "architect"],
                    "industries": []
                  },
                  "nice_to_have": {},
                  "seniority": "mid",
                  "negative_filters": {},
                  "keywords": ["cloud architecture", "architecture"],
                  "embedding_text": "Open roles in cloud architecture",
                  "result_scope": "jobs"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("Open roles in cloud architecture");

        assertTrue(result.isPresent());
        assertNotNull(result.get().mustHave());
        assertNotNull(result.get().niceToHave());
        assertNotNull(result.get().negativeFilters());
        assertEquals("mid", result.get().seniority());
        assertNotNull(result.get().mustHave().skills());
        assertTrue(result.get().mustHave().skills().isEmpty());
        assertNotNull(result.get().mustHave().languages());
        assertTrue(result.get().mustHave().languages().isEmpty());
        assertNotNull(result.get().niceToHave().skills());
        assertTrue(result.get().niceToHave().skills().isEmpty());
        assertNotNull(result.get().negativeFilters().skills());
        assertTrue(result.get().negativeFilters().skills().isEmpty());
        assertNotNull(result.get().negativeFilters().location());
        assertTrue(result.get().negativeFilters().location().isEmpty());
        assertEquals("jobs", result.get().resultScope());
    }

    @Test
    void parse_llmReturnsMarkdownFences_stripsThem() {
        String json = """
                ```json
                {
                  "must_have": {"skills": ["Python"], "languages": [], "industries": []},
                  "nice_to_have": {"skills": [], "industries": []},
                  "seniority": "mid",
                  "keywords": [],
                  "embedding_text": "Mid Python developer",
                  "result_scope": "people"
                }
                ```
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("python dev");
        assertTrue(result.isPresent());
    }

    @Test
    void parse_llmThrows_throws() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("timeout"));

        assertThrows(IllegalStateException.class, () -> parser.parse("test query"));
    }

    @Test
    void parse_llmReturnsNull_throws() {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        when(chatModel.chat(any(), any())).thenReturn(response);

        assertThrows(IllegalStateException.class, () -> parser.parse("query"));
    }

    @Test
    void parse_llmReturnsInvalidJson_throws() {
        mockChatResponse("not valid json {{{");

        assertThrows(IllegalStateException.class, () -> parser.parse("test"));
    }

    @Test
    void parse_llmReturnsBlankText_throws() {
        mockChatResponse("   ");

        assertThrows(IllegalStateException.class, () -> parser.parse("test"));
    }

    @Test
    void parse_invalidResultScope_normalizesToUnknown() {
        String json = """
                {
                  "must_have": {"skills": ["Java"], "languages": [], "industries": []},
                  "nice_to_have": {"skills": [], "industries": []},
                  "seniority": "unknown",
                  "keywords": ["backend"],
                  "embedding_text": "Java backend profile",
                  "result_scope": "vendors"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("java backend profile");

        assertTrue(result.isPresent());
        assertEquals("unknown", result.get().resultScope());
    }

    @Test
    void parse_seniorityWithUnsupportedValue_mapsToUnknown() {
        String json = """
                {
                  "must_have": {"skills": ["Kubernetes"], "languages": [], "industries": []},
                  "nice_to_have": {"skills": [], "industries": []},
                  "seniority": "executive",
                  "keywords": ["platform"],
                  "embedding_text": "VP platform engineering",
                  "result_scope": "people"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("VP platform engineering");

        assertTrue(result.isPresent());
        assertEquals("unknown", result.get().seniority());
    }

    @Test
    void parse_skillsWithLevel_clampsAndDeduplicatesByName() {
        String json = """
                {
                  "must_have": {
                    "skills": [],
                    "skills_with_level": [
                      {"name": "Java", "min_level": 9},
                      {"name": " ", "min_level": 3},
                      {"name": "java", "min_level": 0}
                    ],
                    "roles": [],
                    "languages": [],
                    "location": [],
                    "industries": []
                  },
                  "nice_to_have": {
                    "skills": [],
                    "skills_with_level": [
                      {"name": "Kafka", "min_level": 8}
                    ],
                    "industries": [],
                    "experience": []
                  },
                  "seniority": "sr",
                  "negative_filters": {"seniority": "   ", "skills": ["  ", "Go", "Go"], "location": ["Rome", "Rome"]},
                  "keywords": [" cloud ", "cloud", "  "],
                  "embedding_text": "",
                  "result_scope": "people"
                }
                """;
        mockChatResponse(json);

        Optional<SearchQuery> result = parser.parse("sr cloud engineer");

        assertTrue(result.isPresent());
        SearchQuery parsed = result.get();
        assertEquals(1, parsed.mustHave().skillsWithLevel().size());
        assertEquals("java", parsed.mustHave().skillsWithLevel().get(0).name().toLowerCase());
        assertEquals(1, parsed.mustHave().skillsWithLevel().get(0).minLevel());
        assertEquals(5, parsed.niceToHave().skillsWithLevel().get(0).minLevel());
        assertEquals("unknown", parsed.seniority());
        assertEquals(1, parsed.negativeFilters().skills().size());
        assertEquals("Go", parsed.negativeFilters().skills().get(0));
        assertEquals(1, parsed.negativeFilters().location().size());
        assertEquals("Rome", parsed.negativeFilters().location().get(0));
        assertEquals(1, parsed.keywords().size());
        assertEquals("cloud", parsed.keywords().get(0));
        assertEquals("sr cloud engineer", parsed.embeddingText());
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
