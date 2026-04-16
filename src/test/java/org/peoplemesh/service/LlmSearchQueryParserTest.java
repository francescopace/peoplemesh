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
