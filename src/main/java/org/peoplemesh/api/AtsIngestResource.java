package org.peoplemesh.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.service.JobService;
import org.peoplemesh.service.JobService.AtsJobPayload;

import java.util.*;

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

    private static final Logger LOG = Logger.getLogger(AtsIngestResource.class);
    private static final int MAX_BATCH_SIZE = 100;

    @Inject
    AppConfig config;

    @Inject
    JobService jobService;

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
    public Response ingestJobs(@HeaderParam("X-Maintenance-Key") String key, AtsIngestRequest request) {
        assertAuthorized(key);

        if (request == null || request.jobs == null || request.jobs.isEmpty()) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "jobs array is required"))
                    .build();
        }
        if (request.jobs.size() > MAX_BATCH_SIZE) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "batch size exceeds maximum of " + MAX_BATCH_SIZE))
                    .build();
        }
        if (request.ownerUserId == null) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "owner_user_id is required"))
                    .build();
        }

        List<JobPostingDto> results = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();

        for (AtsJobEntry entry : request.jobs) {
            try {
                validateEntry(entry);
                AtsJobPayload payload = new AtsJobPayload(
                        entry.title,
                        entry.description,
                        entry.requirementsText,
                        entry.skillsRequired,
                        entry.workMode,
                        entry.employmentType,
                        entry.country,
                        entry.status,
                        entry.externalUrl
                );
                JobPostingDto result = jobService.upsertFromAts(request.ownerUserId, entry.externalId, payload);
                results.add(result);
            } catch (Exception e) {
                LOG.warnf("ATS ingest failed for external_id=%s: %s", entry.externalId, e.getMessage());
                errors.add(Map.of(
                        "external_id", entry.externalId != null ? entry.externalId : "",
                        "error", e.getMessage()
                ));
            }
        }

        LOG.infof("ATS ingest: %d upserted, %d failed", results.size(), errors.size());
        return Response.ok(Map.of(
                "upserted", results.size(),
                "failed", errors.size(),
                "errors", errors
        )).build();
    }

    private void validateEntry(AtsJobEntry entry) {
        if (entry.externalId == null || entry.externalId.isBlank()) {
            throw new IllegalArgumentException("external_id is required");
        }
        if (entry.title == null || entry.title.isBlank()) {
            throw new IllegalArgumentException("title is required for external_id: " + entry.externalId);
        }
        if (entry.description == null || entry.description.isBlank()) {
            throw new IllegalArgumentException("description is required for external_id: " + entry.externalId);
        }
    }

    private void assertAuthorized(String key) {
        MaintenanceAuthHelper.assertAuthorized(key, config, httpHeaders);
    }

    public static class AtsIngestRequest {
        public UUID ownerUserId;
        public List<AtsJobEntry> jobs;
    }

    public static class AtsJobEntry {
        public String externalId;
        public String title;
        public String description;
        public String requirementsText;
        public List<String> skillsRequired;
        public String workMode;
        public String employmentType;
        public String country;
        public String status;
        public String externalUrl;
    }
}
