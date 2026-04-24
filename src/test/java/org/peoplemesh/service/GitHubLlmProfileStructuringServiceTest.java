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
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.Seniority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubLlmProfileStructuringServiceTest {

    @Mock
    ChatModel chatModel;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    GitHubLlmProfileStructuringService structuringLlm;

    @Test
    void enrichGitHubImport_appliesLlmClassification() {
        mockChatResponse("""
                {
                  "roles": ["Platform Engineer"],
                  "seniority": "LEAD",
                  "skills_technical": ["Semantic Search"],
                  "tools_and_tech": ["GitHub Actions"],
                  "learning_areas": ["Embeddings"],
                  "project_types": ["Platform Engineering"]
                }
                """);

        OidcSubject subject = new OidcSubject(
                "sub123", "Dev User", "Dev", "User",
                "dev@example.com", "Python developer", null, "en-US",
                "https://img.example/dev.png", "Acme Inc");
        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject,
                List.of("Java"),
                List.of("semantic-search", "github-actions")
        );

        ProfileSchema schema = structuringLlm.enrichGitHubImport(enriched);

        assertNotNull(schema.professional());
        assertEquals(List.of("Platform Engineer"), schema.professional().roles());
        assertEquals(Seniority.LEAD, schema.professional().seniority());
        assertNotNull(schema.professional().skillsTechnical());
        assertTrue(schema.professional().skillsTechnical().contains("Semantic Search"));
        assertTrue(schema.professional().skillsTechnical().contains("Java"));
        assertEquals(List.of("GitHub Actions"), schema.professional().toolsAndTech());
        assertNotNull(schema.interestsProfessional());
        assertEquals(List.of("Embeddings"), schema.interestsProfessional().learningAreas());
        assertEquals(List.of("Platform Engineering"), schema.interestsProfessional().projectTypes());
    }

    @Test
    void enrichGitHubImport_llmFailure_throws() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("timeout"));

        OidcSubject subject = new OidcSubject(
                "sub123", "Dev User", "Dev", "User",
                "dev@example.com", "Python developer", null, "en-US",
                "https://img.example/dev.png", "Acme Inc");
        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject,
                List.of("Java"),
                List.of("semantic-search", "github-actions")
        );

        assertThrows(IllegalStateException.class, () -> structuringLlm.enrichGitHubImport(enriched));
    }

    @Test
    void enrichGitHubImport_keepsToolsFromLlmPayload() {
        mockChatResponse("""
                {
                  "roles": ["Platform Engineer"],
                  "seniority": "LEAD",
                  "skills_technical": ["Semantic Search"],
                  "tools_and_tech": ["Global Tech Conference", "Kubernetes"],
                  "learning_areas": [],
                  "project_types": []
                }
                """);

        OidcSubject subject = new OidcSubject(
                "sub123", "Dev User", "Dev", "User",
                "dev@example.com", "Python developer", null, "en-US",
                "https://img.example/dev.png", "Acme Inc");
        GitHubEnrichedResult enriched = new GitHubEnrichedResult(subject, List.of("Java"), List.of("kubernetes"));

        ProfileSchema schema = structuringLlm.enrichGitHubImport(enriched);

        assertNotNull(schema.professional());
        assertEquals(List.of("Global Tech Conference", "Kubernetes"), schema.professional().toolsAndTech());
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
