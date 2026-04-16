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
import org.peoplemesh.domain.dto.ParsedSearchQuery;

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
                  "embedding_text": "Senior Java developer"
                }
                """;
        mockChatResponse(json);

        Optional<ParsedSearchQuery> result = parser.parse("looking for senior Java dev");

        assertTrue(result.isPresent());
        assertEquals("senior", result.get().seniority());
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
                  "embedding_text": "Java Kubernetes developer in Europe"
                }
                """;
        mockChatResponse(json);

        Optional<ParsedSearchQuery> result = parser.parse("community for java devs in Europe");

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
        assertTrue(prompt.contains("languages = spoken human languages only"));
        assertTrue(prompt.contains("location = geographic places"));
        assertTrue(prompt.contains("industries = business sectors only"));
        assertTrue(prompt.contains("Open roles in cloud architecture"));
        assertTrue(prompt.contains("keep seniority as \"unknown\""));
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
                  "embedding_text": "Open roles in cloud architecture"
                }
                """;
        mockChatResponse(json);

        Optional<ParsedSearchQuery> result = parser.parse("Open roles in cloud architecture");

        assertTrue(result.isPresent());
        assertNotNull(result.get().mustHave());
        assertNotNull(result.get().niceToHave());
        assertNotNull(result.get().negativeFilters());
        assertEquals("unknown", result.get().seniority());
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
                  "embedding_text": "Mid Python developer"
                }
                ```
                """;
        mockChatResponse(json);

        Optional<ParsedSearchQuery> result = parser.parse("python dev");
        assertTrue(result.isPresent());
    }

    @Test
    void parse_llmThrows_returnsEmpty() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("timeout"));

        Optional<ParsedSearchQuery> result = parser.parse("test query");

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_llmReturnsNull_returnsEmpty() {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        when(chatModel.chat(any(), any())).thenReturn(response);

        assertTrue(parser.parse("query").isEmpty());
    }

    @Test
    void parse_llmReturnsInvalidJson_returnsEmpty() {
        mockChatResponse("not valid json {{{");

        assertTrue(parser.parse("test").isEmpty());
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
