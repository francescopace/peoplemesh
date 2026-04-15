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
import org.peoplemesh.domain.dto.ProfileSchema;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmProfileStructuringTest {

    @Mock
    ChatModel chatModel;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    LlmProfileStructuring structuring;

    @Test
    void extractProfile_validResponse_returnsSchema() {
        String json = """
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Backend Engineer"],
                    "seniority": "SENIOR",
                    "skills_technical": ["Java"],
                    "skills_soft": [],
                    "tools_and_tech": ["Quarkus"],
                    "languages_spoken": ["English"]
                  },
                  "geography": {
                    "country": "IT",
                    "city": "Rome"
                  }
                }
                """;
        mockChatResponse(json);

        Optional<ProfileSchema> result = structuring.extractProfile("# CV Content\nSome text");

        assertTrue(result.isPresent());
        assertNotNull(result.get().professional());
    }

    @Test
    void extractProfile_llmThrows_returnsEmpty() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("LLM down"));

        assertTrue(structuring.extractProfile("cv text").isEmpty());
    }

    @Test
    void extractProfile_llmReturnsNull_returnsEmpty() {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        when(chatModel.chat(any(), any())).thenReturn(response);

        assertTrue(structuring.extractProfile("cv text").isEmpty());
    }

    @Test
    void extractProfile_llmReturnsBlank_returnsEmpty() {
        mockChatResponse("  ");

        assertTrue(structuring.extractProfile("cv text").isEmpty());
    }

    @Test
    void extractProfile_invalidJson_returnsEmpty() {
        mockChatResponse("not json at all");

        assertTrue(structuring.extractProfile("cv text").isEmpty());
    }

    @Test
    void extractProfile_markdownFences_stripped() {
        String json = """
                ```json
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Dev"],
                    "seniority": "MID"
                  }
                }
                ```
                """;
        mockChatResponse(json);

        Optional<ProfileSchema> result = structuring.extractProfile("cv");
        assertTrue(result.isPresent());
    }

    @Test
    void extractProfile_nullContent_handlesGracefully() {
        String json = """
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Dev"]
                  }
                }
                """;
        mockChatResponse(json);

        Optional<ProfileSchema> result = structuring.extractProfile(null);
        assertTrue(result.isPresent());
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
