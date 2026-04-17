package org.peoplemesh.api.resource;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.MeService;
import org.peoplemesh.service.SessionService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeResourceTest {

    @Mock
    SecurityIdentity identity;
    @Mock
    CurrentUserService currentUserService;
    @Mock
    MeService meService;
    @Mock
    GdprService gdprService;
    @Mock
    SessionService sessionService;
    @Mock
    UriInfo uriInfo;

    @InjectMocks
    MeResource resource;

    @Test
    void getProfile_identityOnly_anonymous_returns204() {
        when(meService.resolveIdentityPayload(identity)).thenReturn(Optional.empty());
        when(identity.isAnonymous()).thenReturn(true);

        Response response = resource.getProfile(true);

        assertEquals(204, response.getStatus());
    }

    @Test
    void updateSkillAssessments_returnsUpdatedCount() {
        List<SkillAssessmentDto> assessments = List.of(SkillAssessmentDto.forInput(UUID.randomUUID(), 3, true));
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(meService.updateCurrentUserSkillAssessments(userId, assessments)).thenReturn(1);

        Response response = resource.updateSkillAssessments(assessments);

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("updated", 1), response.getEntity());
    }

    @Test
    void deleteAccount_usesSecureCookieFlagFromRequest() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://peoplemesh.test/api/v1/me"));
        when(currentUserService.resolveUserId()).thenReturn(UUID.randomUUID());
        NewCookie cookie = org.mockito.Mockito.mock(NewCookie.class);
        when(sessionService.buildClearCookie(true)).thenReturn(cookie);

        Response response = resource.deleteAccount();

        assertEquals(204, response.getStatus());
        verify(sessionService).buildClearCookie(true);
    }
}
