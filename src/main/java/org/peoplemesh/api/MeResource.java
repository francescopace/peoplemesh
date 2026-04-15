package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.AuditService;
import org.peoplemesh.service.ConsentService;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.EntitlementService;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.OAuthCallbackService;
import org.peoplemesh.service.ProfileService;
import org.peoplemesh.service.SessionService;
import org.peoplemesh.service.SkillAssessmentHelper;
import org.peoplemesh.service.SkillReconciliationService;
import org.peoplemesh.service.UserNotificationService;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    ProfileService profileService;

    @Inject
    GdprService gdprService;

    @Inject
    SessionService sessionService;

    @Inject
    UserNotificationService userNotificationService;

    @Inject
    SkillReconciliationService reconciliationService;

    @Inject
    EntitlementService entitlementService;

    @Inject
    CvImportService cvImportService;

    @Inject
    AppConfig appConfig;

    @Inject
    ConsentService consentService;

    @Inject
    AuditService auditService;

    @Context
    UriInfo uriInfo;

    @GET
    @PermitAll
    public Response getProfile(@QueryParam("identity_only") boolean identityOnly) {
        if (identityOnly) {
            return identityPayload();
        }
        if (identity.isAnonymous()) {
            return Response.noContent().build();
        }
        try {
            UUID userId = userResolver.resolveUserId();
            return profileService.getProfile(userId)
                    .map(schema -> Response.ok(schema).build())
                    .orElse(Response.noContent().build());
        } catch (NotAuthorizedException | SecurityException e) {
            // 204 is intentional for SPA clients: anonymous users should get "no content".
            return Response.noContent().build();
        }
    }

    private Response identityPayload() {
        UUID userId = identity.<UUID>getAttribute("pm.userId");
        String provider = identity.<String>getAttribute("pm.provider");
        String displayName = identity.<String>getAttribute("pm.displayName");
        if (userId != null) {
            return MeshNode.<MeshNode>findByIdOptional(userId)
                    .filter(n -> n.nodeType == NodeType.USER)
                    .map(node -> Response.ok(mePayload(userId, provider, displayName, node)).build())
                    .orElseGet(() -> Response.noContent().build());
        }

        if (identity.isAnonymous()) {
            return Response.noContent().build();
        }

        String subject = identity.getPrincipal().getName();
        return UserIdentity.find("oauthSubject = ?1", subject)
                .<UserIdentity>firstResultOptional()
                .flatMap(user -> MeshNode.<MeshNode>findByIdOptional(user.nodeId)
                        .map(node -> Response.ok(mePayload(user.nodeId, user.oauthProvider, null, node)).build()))
                .orElseGet(() -> Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "User not registered"))
                        .build());
    }

    private Map<String, Object> mePayload(UUID nodeId, String provider, String displayName, MeshNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", nodeId);
        payload.put("provider", provider);
        payload.put("email_present", node.externalId != null && !node.externalId.isBlank());
        payload.put("profile_id", node.id);
        if (displayName != null && !displayName.isBlank()) {
            payload.put("display_name", displayName);
        }
        Map<String, Boolean> entitlements = new LinkedHashMap<>();
        entitlements.put("can_create_job", entitlementService.canCreateJob(nodeId));
        entitlements.put("can_manage_skills", entitlementService.canManageSkills(nodeId));
        payload.put("entitlements", entitlements);
        return payload;
    }

    @PUT
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfile(@Valid ProfileSchema updates) {
        UUID userId = userResolver.resolveUserId();
        profileService.upsertProfile(userId, updates);
        return profileService.getProfile(userId)
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.ok(updates).build());
    }

    @POST
    @Authenticated
    @Path("/import-apply")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response applyImport(@Valid ProfileSchema selectedFields,
                                @QueryParam("source") String source) {
        UUID userId = userResolver.resolveUserId();
        if (source == null || source.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Missing source parameter"))
                    .build();
        }
        profileService.applySelectiveImport(userId, selectedFields, source);
        return profileService.getProfile(userId)
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
    public Response getNotifications(@QueryParam("limit") Integer limit) {
        UUID userId = userResolver.resolveUserId();
        List<UserNotificationDto> notifications = userNotificationService.getRecentNotifications(userId, limit);
        return Response.ok(notifications).build();
    }

    @GET
    @Authenticated
    @Path("/skills")
    public Response getSkillAssessments(@QueryParam("catalog_id") UUID catalogId) {
        UUID userId = userResolver.resolveUserId();
        MeshNode node = MeshNode.findPublishedUserNode(userId).orElse(null);
        if (node == null) {
            return Response.noContent().build();
        }

        List<SkillAssessmentDto> result = new ArrayList<>(
                SkillAssessmentHelper.listAssessments(node.id, catalogId));

        List<SkillAssessmentDto> suggestions = reconciliationService.reconcile(node.id, catalogId);
        result.addAll(suggestions);

        return Response.ok(result).build();
    }

    @PUT
    @Authenticated
    @Path("/skills")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSkillAssessments(List<@Valid SkillAssessmentDto> assessments) {
        UUID userId = userResolver.resolveUserId();
        MeshNode node = MeshNode.findPublishedUserNode(userId).orElse(null);
        if (node == null) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", "No published profile found"))
                    .build();
        }

        reconciliationService.applyReconciliation(node.id, userId, assessments);
        return Response.ok(Map.of("updated", assessments.size())).build();
    }

    @POST
    @Authenticated
    @Path("/cv-import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCv(@RestForm("file") FileUpload file) {
        UUID userId = userResolver.resolveUserId();
        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Missing file"))
                    .build();
        }
        if (file.size() > appConfig.cvImport().maxFileSize()) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                    .entity(ProblemDetail.of(413, "Payload Too Large", "File exceeds maximum size"))
                    .build();
        }

        try (InputStream is = Files.newInputStream(file.filePath())) {
            CvImportService.CvImportResult result = cvImportService.parseCv(
                    is, file.fileName(), file.size(), userId);
            return Response.ok(Map.of(
                    "imported", result.schema(),
                    "source", result.source())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(ProblemDetail.of(502, "Bad Gateway", e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "CV upload processing failed: userId=%s fileName=%s", userId, file.fileName());
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
        List<String> active = consentService.getActiveScopes(userId);
        return Response.ok(Map.of("scopes", OAuthCallbackService.DEFAULT_CONSENT_SCOPES, "active", active)).build();
    }

    @POST
    @Authenticated
    @Path("/consents/{scope}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response grantConsent(@PathParam("scope") String scope,
                                 @Context HttpHeaders headers) {
        if (!OAuthCallbackService.DEFAULT_CONSENT_SCOPES.contains(scope)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Invalid consent scope: " + scope))
                    .build();
        }
        UUID userId = userResolver.resolveUserId();
        String ip = null;
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        }
        consentService.recordConsent(userId, scope,
                ip != null ? HashUtils.sha256(ip) : null);
        auditService.log(userId, "CONSENT_GRANTED", "privacy_consent");
        return Response.ok(Map.of("scope", scope, "status", "granted")).build();
    }

    @DELETE
    @Authenticated
    @Path("/consents/{scope}")
    public Response revokeConsent(@PathParam("scope") String scope) {
        if (!OAuthCallbackService.DEFAULT_CONSENT_SCOPES.contains(scope)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Invalid consent scope: " + scope))
                    .build();
        }
        UUID userId = userResolver.resolveUserId();
        consentService.revokeConsent(userId, scope);
        auditService.log(userId, "CONSENT_REVOKED", "privacy_consent");
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
