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
import org.peoplemesh.domain.exception.NotFoundBusinessException;
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
                null, null, null, null, null, null, null, null, null);
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

    // === match ===

    @Test
    void match_blankProfileJson_returnsError() {
        TextContent result = mcpReadTools.match(null, null, null);
        assertTrue(result.text().contains("profileJson is required"));
    }

    @Test
    void match_oversized_returnsError() {
        TextContent result = mcpReadTools.match(
                "x".repeat(McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE + 1), null, null);
        assertTrue(result.text().contains("exceeds maximum size"));
    }

    @Test
    void match_valid_returnsJson() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        MeshMatchResult one = sampleMatch();
        when(matchesService.matchFromSchema(eq(userId), any(ProfileSchema.class), eq("JOB"), eq("IT")))
                .thenReturn(List.of(one));

        String json = "{\"professional\":{\"roles\":[\"Engineer\"]}}";
        TextContent result = mcpReadTools.match(json, "JOB", "IT");

        assertTrue(result.text().contains(one.title()));
        verify(matchesService).matchFromSchema(eq(userId), any(ProfileSchema.class), eq("JOB"), eq("IT"));
    }

    @Test
    void match_securityException_returnsError() {
        when(currentUserService.resolveUserId()).thenThrow(new SecurityException("denied"));

        TextContent result = mcpReadTools.match("{\"professional\":{\"roles\":[\"X\"]}}", null, null);
        assertTrue(result.text().contains("access denied"));
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

    // === matchNode ===

    @Test
    void matchNode_blankId_returnsError() {
        TextContent result = mcpReadTools.matchNode("  ", null, null);
        assertTrue(result.text().contains("nodeId is required"));
    }

    @Test
    void matchNode_invalidUuid_returnsError() {
        TextContent result = mcpReadTools.matchNode("not-a-uuid", null, null);
        assertTrue(result.text().contains("Invalid nodeId"));
    }

    @Test
    void matchNode_notFound_returnsMessage() {
        UUID nodeUuid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchFromNode(userId, nodeUuid, null, null))
                .thenThrow(new NotFoundBusinessException("Node not found or has no embedding"));
        TextContent result = mcpReadTools.matchNode(nodeUuid.toString(), null, null);
        assertTrue(result.text().contains("Node not found or has no embedding"));
    }

    @Test
    void matchNode_valid_returnsJson() {
        UUID userId = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        MeshMatchResult one = sampleMatch();
        when(matchesService.matchFromNode(eq(userId), eq(nodeUuid), isNull(), isNull()))
                .thenReturn(List.of(one));
        TextContent result = mcpReadTools.matchNode(nodeUuid.toString(), null, null);
        assertTrue(result.text().contains(one.title()));
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
                        0.1, 0.2, 0.0, 0.5, 1.0, 0.85, List.of("java"), null),
                null);
    }
}
