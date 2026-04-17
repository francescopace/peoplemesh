package org.peoplemesh.api.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.api.MaintenanceAuthHelper;
import org.peoplemesh.application.AtsIngestApplicationService;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;

/**
 * Ingest endpoint for ATS (Applicant Tracking System) job feeds.
 * Accepts single or batch job upserts. Protected by X-Maintenance-Key + optional
 * IP allowlist — same mechanism as maintenance endpoints.
 *
 * Each job requires an external_id (the ATS-side identifier) used as the
 * idempotency key: if a JOB node with the same external_id already exists
 * for the owner, it is updated; otherwise a new one is created.
 */
@Path("/api/v1/maintenance/ingest")
@Produces(MediaType.APPLICATION_JSON)
public class AtsIngestResource {

    @Inject
    AppConfig config;

    @Inject
    AtsIngestApplicationService atsIngestApplicationService;

    @Context
    HttpHeaders httpHeaders;

    /**
     * Upsert a batch of jobs from an ATS feed.
     *
     * <pre>
     * POST /api/v1/maintenance/ingest/jobs
     * X-Maintenance-Key: &lt;key&gt;
     * Content-Type: application/json
     *
     * {
     *   "owner_user_id": "uuid",
     *   "jobs": [
     *     {
     *       "external_id": "ats-123",
     *       "title": "...",
     *       "description": "...",
     *       "requirements_text": "...",
     *       "skills_required": ["Java", "Quarkus"],
     *       "work_mode": "REMOTE",
     *       "employment_type": "EMPLOYED",
     *       "country": "IT",
     *       "status": "published"
     *     }
     *   ]
     * }
     * </pre>
     */
    @POST
    @Path("/jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestJobs(@HeaderParam("X-Maintenance-Key") String key,
                               @Valid AtsIngestRequestDto request) {
        assertAuthorized(key);
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        AtsIngestResultDto result = atsIngestApplicationService.ingestJobs(request);
        return Response.ok(result).build();
    }

    private void assertAuthorized(String key) {
        MaintenanceAuthHelper.assertAuthorized(key, config, httpHeaders);
    }
}
