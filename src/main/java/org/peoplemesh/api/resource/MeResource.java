package org.peoplemesh.api.resource;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.MeService;
import org.peoplemesh.service.ConsentService;
import org.peoplemesh.service.ProfileService;
import org.peoplemesh.service.SessionService;
import org.peoplemesh.service.UserNotificationService;
import org.peoplemesh.util.ClientIpResolver;
import org.peoplemesh.util.HashUtils;

import java.util.UUID;
import java.util.List;
import java.util.Map;

@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ProfileService profileService;

    @Inject
    MeService meService;

    @Inject
    GdprService gdprService;

    @Inject
    UserNotificationService userNotificationService;

    @Inject
    SessionService sessionService;

    @Inject
    CvImportService cvImportService;

    @Inject
    AppConfig appConfig;

    @Context
    UriInfo uriInfo;

    @GET
    @PermitAll
    public Response getProfile(@QueryParam("identity_only") boolean identityOnly) {
        var maybeUserId = currentUserService.findCurrentUserId();
        if (maybeUserId.isEmpty()) {
            return Response.noContent().build();
        }
        if (identityOnly) {
            return meService.resolveIdentityPayload(identity)
                    .map(payload -> Response.ok(payload).build())
                    .orElse(Response.noContent().build());
        }
        return profileService.getProfile(maybeUserId.get())
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.noContent().build());
    }

    @PUT
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfile(@Valid ProfileSchema updates) {
        UUID userId = currentUserService.resolveUserId();
        ProfileSchema profile = meService.resolveProfile(userId, updates);
        return Response.ok(profile).build();
    }

    @PATCH
    @Authenticated
    @Consumes("application/merge-patch+json")
    public Response patchProfile(JsonNode mergePatch) {
        UUID userId = currentUserService.resolveUserId();
        ProfileSchema profile = meService.patchProfile(userId, mergePatch);
        return Response.ok(profile).build();
    }

    @POST
    @Authenticated
    @Path("/import-apply")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response applyImport(
            @Valid ProfileSchema selectedFields,
            @QueryParam("source") @Size(max = 50) @Pattern(regexp = "^[a-zA-Z0-9_-]*$") String source) {
        UUID userId = currentUserService.resolveUserId();
        meService.applySelectiveImport(userId, selectedFields, source);
        return profileService.getProfile(userId)
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.noContent().build());
    }

    @DELETE
    @Authenticated
    public Response deleteAccount() {
        UUID userId = currentUserService.resolveUserId();
        gdprService.deleteAllData(userId);
        boolean secure = uriInfo.getRequestUri().getScheme().equalsIgnoreCase("https");
        NewCookie clearCookie = buildClearCookie(secure);
        return Response.noContent().cookie(clearCookie).build();
    }

    @GET
    @Authenticated
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportData() {
        UUID userId = currentUserService.resolveUserId();
        String json = gdprService.exportAllData(userId);
        return Response.ok(json)
                .header("Content-Disposition", "attachment; filename=\"peoplemesh-data-export.json\"")
                .build();
    }

    @GET
    @Authenticated
    @Path("/notifications")
    public Response getNotifications(@QueryParam("limit") @DefaultValue("20") @Min(1) @Max(100) Integer limit) {
        UUID userId = currentUserService.resolveUserId();
        List<UserNotificationDto> notifications = userNotificationService.getRecentNotifications(userId, limit);
        return Response.ok(notifications).build();
    }

    @POST
    @Authenticated
    @Path("/cv-import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCv(@RestForm("file") FileUpload file) {
        UUID userId = currentUserService.resolveUserId();
        CvImportService.CvImportResult result = cvImportService.importFromUpload(
                file != null ? file.filePath() : null,
                file != null ? file.fileName() : null,
                file != null ? file.size() : 0L,
                appConfig.cvImport().maxFileSize(),
                userId);
        return Response.ok(Map.of("imported", result.schema(), "source", result.source())).build();
    }

    @GET
    @Authenticated
    @Path("/consents")
    public Response getConsents() {
        UUID userId = currentUserService.resolveUserId();
        return Response.ok(meService.getConsentView(userId)).build();
    }

    @POST
    @Authenticated
    @Path("/consents/{scope}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response grantConsent(
            @PathParam("scope")
            @Pattern(regexp = "^[a-z_]+$", message = "scope format is invalid")
            String scope,
            @Context HttpHeaders headers
    ) {
        UUID userId = currentUserService.resolveUserId();
        meService.grantConsent(
                userId,
                scope,
                ConsentService.DEFAULT_CONSENT_SCOPES,
                resolveClientIpHash(headers));
        return Response.ok(Map.of("scope", scope, "status", "granted")).build();
    }

    @DELETE
    @Authenticated
    @Path("/consents/{scope}")
    public Response revokeConsent(
            @PathParam("scope")
            @Pattern(regexp = "^[a-z_]+$", message = "scope format is invalid")
            String scope
    ) {
        UUID userId = currentUserService.resolveUserId();
        meService.revokeConsent(userId, scope, ConsentService.DEFAULT_CONSENT_SCOPES);
        return Response.ok(Map.of("scope", scope, "status", "revoked")).build();
    }

    @GET
    @Authenticated
    @Path("/activity")
    public Response getPrivacyDashboard() {
        UUID userId = currentUserService.resolveUserId();
        return Response.ok(gdprService.getPrivacyDashboard(userId)).build();
    }

    private NewCookie buildClearCookie(boolean secure) {
        return new NewCookie.Builder(SessionService.COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(secure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private static String resolveClientIpHash(HttpHeaders headers) {
        var resolved = ClientIpResolver.resolveClientIp(headers)
                .map(HashUtils::sha256);
        return resolved.isPresent() ? resolved.get() : null;
    }
}
