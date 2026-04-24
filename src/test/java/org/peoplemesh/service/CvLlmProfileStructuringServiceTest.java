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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvLlmProfileStructuringServiceTest {

    @Mock
    ChatModel chatModel;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    CvLlmProfileStructuringService structuring;

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

        ProfileSchema result = structuring.extractProfile("# CV Content\nSome text");

        assertNotNull(result);
        assertNotNull(result.professional());
    }

    @Test
    void extractProfile_llmThrows_throws() {
        when(chatModel.chat(any(), any())).thenThrow(new RuntimeException("LLM down"));

        assertThrows(IllegalStateException.class, () -> structuring.extractProfile("cv text"));
    }

    @Test
    void extractProfile_llmReturnsNull_throws() {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        when(chatModel.chat(any(), any())).thenReturn(response);

        assertThrows(IllegalStateException.class, () -> structuring.extractProfile("cv text"));
    }

    @Test
    void extractProfile_llmReturnsBlank_throws() {
        mockChatResponse("  ");

        assertThrows(IllegalStateException.class, () -> structuring.extractProfile("cv text"));
    }

    @Test
    void extractProfile_invalidJson_throws() {
        mockChatResponse("not json at all");

        assertThrows(IllegalStateException.class, () -> structuring.extractProfile("cv text"));
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

        ProfileSchema result = structuring.extractProfile("cv");
        assertNotNull(result);
    }

    @Test
    void extractProfile_keepsToolsAndTechFromPayload() {
        String json = """
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Commercial Account Executive"],
                    "tools_and_tech": ["GitLab", "Red Hat Summit", "Global Tech Conference", "AWS Certified Solutions Architect", "Kubernetes"]
                  }
                }
                """;
        mockChatResponse(json);

        ProfileSchema result = structuring.extractProfile("cv");

        assertNotNull(result.professional());
        assertEquals(
                java.util.List.of(
                        "GitLab",
                        "Red Hat Summit",
                        "Global Tech Conference",
                        "AWS Certified Solutions Architect",
                        "Kubernetes"
                ),
                result.professional().toolsAndTech()
        );
    }

    @Test
    void extractProfile_sanitizesIndustriesProjectTypesLearningAreasAndKeepsCityText() {
        String json = """
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Commercial Account Executive"],
                    "industries": ["Information Technology", "cloud", "Internet of Things"]
                  },
                  "interests_professional": {
                    "learning_areas": ["DevOps practices", "Cloud-native architectures", "IoT and edge computing technologies"],
                    "project_types": ["platform engineering", "developer portal", "pre sales enablement"]
                  },
                  "geography": {
                    "country": "italy",
                    "city": "Remote - Rome"
                  }
                }
                """;
        mockChatResponse(json);

        ProfileSchema result = structuring.extractProfile("cv");

        assertEquals(
                java.util.List.of("Information Technology", "cloud", "Internet of Things"),
                result.professional().industries()
        );
        assertNotNull(result.interestsProfessional());
        assertEquals(
                java.util.List.of("DevOps practices", "Cloud-native architectures", "IoT and edge computing technologies"),
                result.interestsProfessional().learningAreas()
        );
        assertEquals(
                java.util.List.of("platform engineering", "developer portal", "pre sales enablement"),
                result.interestsProfessional().projectTypes()
        );
        assertNotNull(result.geography());
        assertNull(result.geography().country());
        assertEquals("Remote - Rome", result.geography().city());
    }

    @Test
    void extractProfile_personalEducationObject_isNormalizedToStringList() {
        String json = """
                {
                  "profile_version": "1.0",
                  "professional": {
                    "roles": ["Dev"]
                  },
                  "personal": {
                    "education": {
                      "degree": "MSc Computer Science",
                      "university": "Sapienza"
                    }
                  }
                }
                """;
        mockChatResponse(json);

        ProfileSchema result = structuring.extractProfile("cv");

        assertNotNull(result.personal());
        assertEquals(java.util.List.of("MSc Computer Science", "Sapienza"), result.personal().education());
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

        ProfileSchema result = structuring.extractProfile(null);
        assertNotNull(result);
    }

    @Test
    void privateHelpers_normalizeAndExtractJsonCandidate_coverBranches() throws Exception {
        Method extractJsonCandidate = CvLlmProfileStructuringService.class.getDeclaredMethod("extractJsonCandidate", String.class);
        extractJsonCandidate.setAccessible(true);
        assertEquals("", extractJsonCandidate.invoke(null, new Object[]{null}));
        assertEquals("abc", extractJsonCandidate.invoke(null, "abc"));
        assertEquals("{\"a\":1}", extractJsonCandidate.invoke(null, "prefix {\"a\":1} suffix"));

        Method parseProfileSchema = CvLlmProfileStructuringService.class.getDeclaredMethod("parseProfileSchema", String.class);
        parseProfileSchema.setAccessible(true);
        ProfileSchema parsed = (ProfileSchema) parseProfileSchema.invoke(structuring, """
                {
                  "professional": {
                    "roles": " Engineer ",
                    "seniority": "mid",
                    "slack_handle": "@legacy-slack",
                    "employment_type": "invalid"
                  },
                  "contacts": {
                    "mobile_phone": ["+39123"]
                  },
                  "personal": {
                    "education": {"degree": "MSc", "school": "Sapienza"}
                  }
                }
                """);
        assertNotNull(parsed.professional());
        assertEquals("MID", parsed.professional().seniority().name());
        assertNull(parsed.professional().employmentType());
        assertNotNull(parsed.contacts());
        assertEquals("@legacy-slack", parsed.contacts().slackHandle());
        assertEquals("+39123", parsed.contacts().mobilePhone());
        assertNotNull(parsed.personal());
        assertEquals(java.util.List.of("MSc", "Sapienza"), parsed.personal().education());
    }

    private void mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(text);
        when(chatModel.chat(any(), any())).thenReturn(response);
    }
}
