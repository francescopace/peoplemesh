package org.peoplemesh.api.resource;

import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.MeService;
import org.peoplemesh.service.ProfileService;
import org.peoplemesh.service.SessionService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeResourceTest {

    @Mock
    SecurityIdentity identity;
    @Mock
    CurrentUserService currentUserService;
    @Mock
    ProfileService profileService;
    @Mock
    MeService meService;
    @Mock
    GdprService gdprService;
    @Mock
    CvImportService cvImportService;
    @Mock
    SessionService sessionService;
    @Mock
    AppConfig appConfig;
    @Mock
    AppConfig.CvImportConfig cvImportConfig;
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
    void getProfile_whenCurrentUserCannotBeResolved_returns204() {
        when(currentUserService.findCurrentUserId()).thenReturn(Optional.empty());

        Response response = resource.getProfile(false);

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

        Response response = resource.deleteAccount();

        assertEquals(204, response.getStatus());
        NewCookie cookie = response.getCookies().get(SessionService.COOKIE_NAME);
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
        assertEquals("", cookie.getValue());
        assertEquals(true, cookie.isSecure());
    }

    @Test
    void uploadCv_missingFile_throwsValidationBusinessException() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(appConfig.cvImport()).thenReturn(cvImportConfig);
        when(cvImportConfig.maxFileSize()).thenReturn(5L);
        when(cvImportService.importFromUpload(null, null, 0L, 5L, userId))
                .thenThrow(new ValidationBusinessException("Missing file"));

        ValidationBusinessException error = assertThrows(
                ValidationBusinessException.class,
                () -> resource.uploadCv(null));
        assertEquals("Missing file", error.publicDetail());
    }

    @Test
    void uploadCv_fileTooLarge_throwsPayloadTooLargeBusinessException() {
        FileUpload file = org.mockito.Mockito.mock(FileUpload.class);
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(file.size()).thenReturn(10L);
        when(appConfig.cvImport()).thenReturn(cvImportConfig);
        when(cvImportConfig.maxFileSize()).thenReturn(5L);
        when(cvImportService.importFromUpload(null, null, 10L, 5L, userId))
                .thenThrow(new BusinessException(413, "Payload Too Large", "File exceeds maximum size"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> resource.uploadCv(file));
        assertEquals(413, error.status());
        assertEquals("File exceeds maximum size", error.publicDetail());
    }
}
