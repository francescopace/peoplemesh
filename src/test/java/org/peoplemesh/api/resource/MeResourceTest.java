package org.peoplemesh.api.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.ConsentService;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.ProfileService;
import org.peoplemesh.service.SessionService;
import org.peoplemesh.service.UserNotificationService;
import org.peoplemesh.util.HashUtils;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeResourceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    CurrentUserService currentUserService;
    @Mock
    ProfileService profileService;
    @Mock
    ConsentService consentService;
    @Mock
    GdprService gdprService;
    @Mock
    UserNotificationService userNotificationService;
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
    @Mock
    HttpHeaders headers;

    @InjectMocks
    MeResource resource;

    @Test
    void getProfile_anonymous_returns204() {
        when(currentUserService.findCurrentUserId()).thenReturn(Optional.empty());

        Response response = resource.getProfile();

        assertEquals(204, response.getStatus());
    }

    @Test
    void getProfile_whenCurrentUserCannotBeResolved_returns204() {
        when(currentUserService.findCurrentUserId()).thenReturn(Optional.empty());

        Response response = resource.getProfile();

        assertEquals(204, response.getStatus());
    }

    @Test
    void getProfile_whenCurrentUserHasProfile_returns200() {
        UUID userId = UUID.randomUUID();
        ProfileSchema schema = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(List.of("Engineer"), null, null, List.of("Java"), null, null, null, null, null),
                null, null, null, null, null, null);
        when(currentUserService.findCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.getProfile(userId)).thenReturn(Optional.of(schema));

        Response response = resource.getProfile();

        assertEquals(200, response.getStatus());
        assertEquals(schema, response.getEntity());
    }

    @Test
    void getProfile_whenCurrentUserProfileMissing_returns204() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.findCurrentUserId()).thenReturn(Optional.of(userId));
        when(profileService.getProfile(userId)).thenReturn(Optional.empty());

        Response response = resource.getProfile();

        assertEquals(204, response.getStatus());
    }

    @Test
    void updateProfile_returnsResolvedProfile() {
        UUID userId = UUID.randomUUID();
        ProfileSchema updates = new ProfileSchema(null, null, null, null, null, null, null, null, null, null);
        ProfileSchema resolved = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(List.of("Engineer"), null, null, List.of("Java"), null, null, null, null, null),
                null, null, null, null, null, null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(profileService.updateProfile(userId, updates)).thenReturn(resolved);

        Response response = resource.updateProfile(updates);

        assertEquals(200, response.getStatus());
        assertEquals(resolved, response.getEntity());
    }

    @Test
    void patchProfile_returnsResolvedProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode patch = OBJECT_MAPPER.readTree("""
                {
                  "identity": {
                    "birth_date": "1992-04-12"
                  }
                }
                """);
        ProfileSchema resolved = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(List.of("Engineer"), null, null, List.of("Java"), null, null, null, null, null),
                null, null, null, null, null, null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(profileService.patchProfile(userId, patch)).thenReturn(resolved);

        Response response = resource.patchProfile(patch);

        assertEquals(200, response.getStatus());
        assertEquals(resolved, response.getEntity());
    }

    @Test
    void patchProfile_missingPayload_throwsValidationBusinessException() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(profileService.patchProfile(userId, null)).thenThrow(new ValidationBusinessException("Missing merge patch payload"));

        ValidationBusinessException error = assertThrows(
                ValidationBusinessException.class,
                () -> resource.patchProfile(null));
        assertEquals("Missing merge patch payload", error.publicDetail());
    }

    @Test
    void applyImport_returnsUpdatedProfileWhenPresent() {
        UUID userId = UUID.randomUUID();
        ProfileSchema selected = new ProfileSchema(null, null, null, null, null, null, null, null, null, null);
        ProfileSchema profile = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(List.of("Engineer"), null, null, List.of("Java"), null, null, null, null, null),
                null, null, null, null, null, null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(profileService.getProfile(userId)).thenReturn(Optional.of(profile));

        Response response = resource.applyImport(selected, "github");

        assertEquals(200, response.getStatus());
        assertEquals(profile, response.getEntity());
        verify(profileService).applySelectiveImport(userId, selected, "github");
    }

    @Test
    void applyImport_returns204WhenNoProfileAfterImport() {
        UUID userId = UUID.randomUUID();
        ProfileSchema selected = new ProfileSchema(null, null, null, null, null, null, null, null, null, null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(profileService.getProfile(userId)).thenReturn(Optional.empty());

        Response response = resource.applyImport(selected, "github");

        assertEquals(204, response.getStatus());
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
    void exportData_returnsAttachmentPayload() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(gdprService.exportAllData(userId)).thenReturn("{\"ok\":true}");

        Response response = resource.exportData();

        assertEquals(200, response.getStatus());
        assertEquals("{\"ok\":true}", response.getEntity());
        assertEquals("attachment; filename=\"peoplemesh-data-export.json\"",
                response.getHeaderString("Content-Disposition"));
    }

    @Test
    void getNotifications_returnsRecentItems() {
        UUID userId = UUID.randomUUID();
        List<UserNotificationDto> notifications = List.of(
                new UserNotificationDto(UUID.randomUUID(), "Subject", "ACTION", "tool", "{}", Instant.now()));
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(userNotificationService.getRecentNotifications(userId, 20)).thenReturn(notifications);

        Response response = resource.getNotifications(20);

        assertEquals(200, response.getStatus());
        assertEquals(notifications, response.getEntity());
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

    @Test
    void uploadCv_success_returnsImportedSchemaAndSource() {
        FileUpload file = org.mockito.Mockito.mock(FileUpload.class);
        UUID userId = UUID.randomUUID();
        ProfileSchema schema = new ProfileSchema(null, null, null, null, null, null, null, null, null, null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(file.filePath()).thenReturn(java.nio.file.Path.of("/tmp/file.pdf"));
        when(file.fileName()).thenReturn("file.pdf");
        when(file.size()).thenReturn(100L);
        when(appConfig.cvImport()).thenReturn(cvImportConfig);
        when(cvImportConfig.maxFileSize()).thenReturn(1000L);
        when(cvImportService.importFromUpload(java.nio.file.Path.of("/tmp/file.pdf"), "file.pdf", 100L, 1000L, userId))
                .thenReturn(new CvImportService.CvImportResult(schema, "cv_docling_llm"));

        Response response = resource.uploadCv(file);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("cv_docling_llm", body.get("source"));
        assertEquals(schema, body.get("imported"));
    }

    @Test
    void getConsents_returnsConsentView() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> view = Map.of("active", List.of("profile"));
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(consentService.getConsentView(userId)).thenReturn(view);

        Response response = resource.getConsents();

        assertEquals(200, response.getStatus());
        assertEquals(view, response.getEntity());
    }

    @Test
    void grantConsent_hashesClientIpAndReturnsGrantedStatus() {
        UUID userId = UUID.randomUUID();
        String ip = "203.0.113.42";
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(headers.getHeaderString("X-Forwarded-For")).thenReturn(ip + ", 10.0.0.1");

        Response response = resource.grantConsent("profile", headers);

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("scope", "profile", "status", "granted"), response.getEntity());
        verify(consentService).grantConsent(eq(userId), eq("profile"), any(), eq(HashUtils.sha256(ip)));
    }

    @Test
    void revokeConsent_returnsRevokedStatus() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);

        Response response = resource.revokeConsent("profile");

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("scope", "profile", "status", "revoked"), response.getEntity());
        verify(consentService).revokeConsent(eq(userId), eq("profile"), any());
    }

    @Test
    void getPrivacyDashboard_returnsDashboardPayload() {
        UUID userId = UUID.randomUUID();
        PrivacyDashboard dashboard = new PrivacyDashboard(Instant.now(), true, 2, List.of("profile", "search"));
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(gdprService.getPrivacyDashboard(userId)).thenReturn(dashboard);

        Response response = resource.getPrivacyDashboard();

        assertEquals(200, response.getStatus());
        assertEquals(dashboard, response.getEntity());
    }
}
