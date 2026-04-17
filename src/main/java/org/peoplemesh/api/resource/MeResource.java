package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.application.MeApplicationService;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.MeService;
import org.peoplemesh.service.OAuthCallbackService;
import org.peoplemesh.service.SessionService;
import org.peoplemesh.service.UserNotificationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MeResource {

    private static final Logger LOG = Logger.getLogger(MeResource.class);

    @Inject
    SecurityIdentity identity;

    @Inject
    UserResolver userResolver;

    @Inject
    GdprService gdprService;

    @Inject
    SessionService sessionService;

    @Inject
    UserNotificationService userNotificationService;

    @Inject
    MeService meService;

    @Inject
    MeApplicationService meApplicationService;

    @Context
    UriInfo uriInfo;

    @GET
    @PermitAll
    public Response getProfile(@QueryParam("identity_only") boolean identityOnly) {
        if (identityOnly) {
            return identityPayload();
        }
        try {
            return meApplicationService.getCurrentProfile(identity)
                    .map(schema -> Response.ok(schema).build())
                    .orElse(Response.noContent().build());
        } catch (jakarta.ws.rs.NotAuthorizedException | SecurityException e) {
            return Response.noContent().build();
        }
    }

    private Response identityPayload() {
        return meApplicationService.getIdentityPayload(identity)
                .map(payload -> Response.ok(payload).build())
                .orElseGet(() -> identity.isAnonymous()
                        ? Response.noContent().build()
                        : Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "User not registered"))
                        .build());
    }

    @PUT
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfile(@Valid ProfileSchema updates) {
        return Response.ok(meApplicationService.upsertCurrentProfile(updates)).build();
    }

    @POST
    @Authenticated
    @Path("/import-apply")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response applyImport(@Valid ProfileSchema selectedFields, @QueryParam("source") String source) {
        return meApplicationService.applyImport(selectedFields, source)
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.noContent().build());
    }

    @DELETE
    @Authenticated
    public Response deleteAccount() {
        UUID userId = userResolver.resolveUserId();
        gdprService.deleteAllData(userId);
        boolean secure = uriInfo.getRequestUri().getScheme().equalsIgnoreCase("https");
        NewCookie clearCookie = sessionService.buildClearCookie(secure);
        return Response.noContent().cookie(clearCookie).build();
    }

    @GET
    @Authenticated
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportData() {
        UUID userId = userResolver.resolveUserId();
        String json = gdprService.exportAllData(userId);
        return Response.ok(json)
                .header("Content-Disposition", "attachment; filename=\"peoplemesh-data-export.json\"")
                .build();
    }

    @GET
    @Authenticated
    @Path("/notifications")
    public Response getNotifications(@QueryParam("limit") @DefaultValue("20") @Min(1) @Max(100) Integer limit) {
        UUID userId = userResolver.resolveUserId();
        List<UserNotificationDto> notifications = userNotificationService.getRecentNotifications(userId, limit);
        return Response.ok(notifications).build();
    }

    @GET
    @Authenticated
    @Path("/skills")
    public Response getSkillAssessments(@QueryParam("catalog_id") UUID catalogId) {
        UUID userId = userResolver.resolveUserId();
        List<SkillAssessmentDto> result = meService.listCurrentUserSkillAssessments(userId, catalogId);
        if (result.isEmpty()) {
            return Response.noContent().build();
        }
        return Response.ok(result).build();
    }

    @PUT
    @Authenticated
    @Path("/skills")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSkillAssessments(
            @NotNull @Size(max = 500) List<@Valid SkillAssessmentDto> assessments) {
        UUID userId = userResolver.resolveUserId();
        int updated = meService.updateCurrentUserSkillAssessments(userId, assessments);
        return Response.ok(Map.of("updated", updated)).build();
    }

    @POST
    @Authenticated
    @Path("/cv-import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCv(@RestForm("file") FileUpload file) {
        try {
            CvImportService.CvImportResult result = meApplicationService.parseCv(file);
            return Response.ok(Map.of("imported", result.schema(), "source", result.source())).build();
        } catch (org.peoplemesh.domain.exception.ValidationBusinessException e) {
            if ("File exceeds maximum size".equals(e.publicDetail())) {
                return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                        .entity(ProblemDetail.of(413, "Payload Too Large", e.publicDetail()))
                        .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", e.publicDetail()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(ProblemDetail.of(502, "Bad Gateway", "CV processing failed"))
                    .build();
        } catch (Exception e) {
            UUID userId = userResolver.resolveUserId();
            LOG.errorf(e, "CV upload processing failed for userId=%s", userId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ProblemDetail.of(500, "Internal Server Error", "Error processing file"))
                    .build();
        }
    }

    @GET
    @Authenticated
    @Path("/consents")
    public Response getConsents() {
        UUID userId = userResolver.resolveUserId();
        List<String> active = meService.getActiveConsentScopes(userId);
        return Response.ok(Map.of("scopes", OAuthCallbackService.DEFAULT_CONSENT_SCOPES, "active", active)).build();
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
        UUID userId = userResolver.resolveUserId();
        meService.grantConsent(userId, scope, OAuthCallbackService.DEFAULT_CONSENT_SCOPES, headers);
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
        UUID userId = userResolver.resolveUserId();
        meService.revokeConsent(userId, scope, OAuthCallbackService.DEFAULT_CONSENT_SCOPES);
        return Response.ok(Map.of("scope", scope, "status", "revoked")).build();
    }

    @GET
    @Authenticated
    @Path("/activity")
    public Response getPrivacyDashboard() {
        UUID userId = userResolver.resolveUserId();
        PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);
        return Response.ok(dashboard).build();
    }
}
