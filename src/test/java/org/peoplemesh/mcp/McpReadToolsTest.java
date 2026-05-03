package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.MatchesService;
import org.peoplemesh.service.ProfileService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpReadToolsTest {

    @Mock
    CurrentUserService currentUserService;
    @Mock
    ProfileService profileService;
    @Mock
    MatchesService matchesService;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    McpReadTools mcpReadTools;

    // === getMyProfile ===

    @Test
    void getMyProfile_noProfile_returnsNotFound() {
        when(currentUserService.resolveUserId()).thenReturn(UUID.randomUUID());
        when(profileService.getProfile(any())).thenReturn(Optional.empty());

        TextContent result = mcpReadTools.getMyProfile();
        assertTrue(result.text().contains("No profile found"));
    }

    @Test
    void getMyProfile_found_returnsPrettyJson() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        ProfileSchema schema = new ProfileSchema(
                null, null, null, null, null, null, null, null, null, null);
        when(profileService.getProfile(userId)).thenReturn(Optional.of(schema));

        TextContent result = mcpReadTools.getMyProfile();
        assertTrue(result.text().contains("profile_version") || result.text().contains("{"));
        assertFalse(result.text().contains("No profile found"));
    }

    @Test
    void getMyProfile_securityException_returnsError() {
        when(currentUserService.resolveUserId()).thenThrow(new SecurityException("not registered"));

        TextContent result = mcpReadTools.getMyProfile();
        assertTrue(result.text().contains("access denied"));
    }

    // === matchPrompt ===

    @Test
    void matchPrompt_blankQuery_returnsError() {
        TextContent result = mcpReadTools.matchPrompt(null, null, null);
        assertTrue(result.text().contains("query is required"));
    }

    @Test
    void matchPrompt_oversized_returnsError() {
        TextContent result = mcpReadTools.matchPrompt("x".repeat(501), null, null);
        assertTrue(result.text().contains("exceeds maximum length"));
    }

    @Test
    void matchPrompt_valid_returnsJson() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        SearchRequest request = new SearchRequest("java engineer");
        when(matchesService.matchFromPrompt(eq(userId), eq(request), eq("JOB"), eq("IT"), isNull()))
                .thenReturn(new SearchResponse(null, List.of()));

        TextContent result = mcpReadTools.matchPrompt("java engineer", "JOB", "IT");

        assertTrue(result.text().contains("\"results\""));
        verify(matchesService).matchFromPrompt(eq(userId), eq(request), eq("JOB"), eq("IT"), isNull());
    }

    @Test
    void matchPrompt_securityException_returnsError() {
        when(currentUserService.resolveUserId()).thenThrow(new SecurityException("denied"));

        TextContent result = mcpReadTools.matchPrompt("java engineer", null, null);
        assertTrue(result.text().contains("access denied"));
    }

    @Test
    void matchPrompt_businessException_returnsPublicDetail() {
        UUID userId = UUID.randomUUID();
        SearchRequest request = new SearchRequest("java engineer");
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchFromPrompt(eq(userId), eq(request), isNull(), isNull(), isNull()))
                .thenThrow(new ForbiddenBusinessException("not allowed"));

        TextContent result = mcpReadTools.matchPrompt("java engineer", null, null);
        assertTrue(result.text().contains("not allowed"));
    }

    // === matchMe ===

    @Test
    void matchMe_noResults_returnsEmptyJsonArray() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchMyProfile(userId, "PEOPLE", null)).thenReturn(Collections.emptyList());

        TextContent result = mcpReadTools.matchMe("PEOPLE", null);

        assertTrue(result.text().contains("["));
        assertTrue(result.text().contains("]"));
    }

    @Test
    void matchMe_withResults_returnsJson() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        MeshMatchResult one = sampleMatch();
        when(matchesService.matchMyProfile(userId, null, "DE")).thenReturn(List.of(one));

        TextContent result = mcpReadTools.matchMe(null, "DE");

        assertTrue(result.text().contains(one.title()));
    }

    @Test
    void matchMe_businessException_returnsError() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchMyProfile(userId, null, null))
                .thenThrow(new ForbiddenBusinessException("forbidden"));

        TextContent result = mcpReadTools.matchMe(null, null);
        assertTrue(result.text().contains("forbidden"));
    }

    @Test
    void matchMe_securityException_returnsAccessDenied() {
        when(currentUserService.resolveUserId()).thenThrow(new SecurityException("x"));
        TextContent result = mcpReadTools.matchMe(null, null);
        assertTrue(result.text().contains("access denied"));
    }

    private static MeshMatchResult sampleMatch() {
        return new MeshMatchResult(
                UUID.randomUUID(),
                "JOB",
                "Acme Role",
                "Desc",
                null,
                List.of("java"),
                "US",
                0.85,
                new MeshMatchResult.MeshMatchBreakdown(
                        0.1, 0.2, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 0.85,
                        List.of("java"), null, List.of("java"), List.of(), List.of(), List.of(),
                        true, false, false, false, false,
                        null, null, null, null, null, null, null, null, null, null, null, null),
                null);
    }
}
