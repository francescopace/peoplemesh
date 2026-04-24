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
import org.peoplemesh.domain.dto.ClusterName;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmClusterNamingTest {

    @Mock
    ChatModel chatModel;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    LlmClusterNaming naming;

    @Test
    void generateName_validResponse_returnsParsed() {
        String json = """
                {"title": "Tech Innovators", "description": "A group of tech enthusiasts.", "tags": ["tech", "innovation"]}
                """;
        mockChatResponse(json);

        Map<String, List<String>> traits = Map.of("skills", List.of("Java", "Python"));
        Optional<ClusterName> result = naming.generateName(traits);

        assertTrue(result.isPresent());
        assertEquals("Tech Innovators", result.get().title());
        assertEquals(2, result.get().tags().size());
    }

    @Test
    void generateName_llmThrows_throws() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("timeout"));

        Map<String, List<String>> traits = Map.of(
                "skills", List.of("Java", "Python", "Go"));
        assertThrows(IllegalStateException.class, () -> naming.generateName(traits));
    }

    @Test
    void generateName_llmReturnsBlank_throws() {
        mockChatResponse("");

        Map<String, List<String>> traits = Map.of(
                "skills", List.of("Java", "Quarkus"));
        assertThrows(IllegalStateException.class, () -> naming.generateName(traits));
    }

    @Test
    void generateName_llmReturnsNull_throws() {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        when(chatModel.chat(any(), any())).thenReturn(response);

        Map<String, List<String>> traits = Map.of(
                "skills", List.of("React"));

        assertThrows(IllegalStateException.class, () -> naming.generateName(traits));
    }

    @Test
    void generateName_invalidJson_throws() {
        mockChatResponse("not json");

        Map<String, List<String>> traits = Map.of(
                "skills", List.of("Rust"));
        assertThrows(IllegalStateException.class, () -> naming.generateName(traits));
    }

    @Test
    void generateName_emptyTitleInResponse_throws() {
        mockChatResponse("{\"title\": \"\", \"description\": \"desc\", \"tags\": []}");

        Map<String, List<String>> traits = Map.of(
                "skills", List.of("Java"));
        assertThrows(IllegalStateException.class, () -> naming.generateName(traits));
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
