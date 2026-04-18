package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.MatchesService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchesResourceTest {

    @Mock
    CurrentUserService currentUserService;
    @Mock
    MatchesService matchesService;

    @InjectMocks
    MatchesResource resource;

    @Test
    void matchMyProfile_returns200() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchMyProfile(userId, "JOB", "IT", 9, 18)).thenReturn(List.of(
                new MeshMatchResult(
                        UUID.randomUUID(), "JOB", "Role", "Desc", null, List.of("java"), "IT",
                        0.9, null, null)
        ));

        Response response = resource.matchMyProfile("JOB", "IT", 9, 18);

        assertEquals(200, response.getStatus());
    }

    @Test
    void matchFromPrompt_withPagination_returns200() {
        UUID userId = UUID.randomUUID();
        SearchRequest request = new SearchRequest("java developer");
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(matchesService.matchFromPrompt(userId, request, 9, 18))
                .thenReturn(new SearchResponse(null, List.of()));

        Response response = resource.matchFromPrompt(request, 9, 18);

        assertEquals(200, response.getStatus());
    }
}
