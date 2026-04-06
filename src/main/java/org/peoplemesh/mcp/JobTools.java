package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.JobMatchResult;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.dto.JobPostingPayload;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MatchResult;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.service.JobService;
import org.peoplemesh.service.MatchingService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class JobTools {

    private static final Logger LOG = Logger.getLogger(JobTools.class);
    private static final int MAX_PAYLOAD_SIZE = 64 * 1024;

    @Inject
    UserResolver userResolver;

    @Inject
    JobService jobService;

    @Inject
    MatchingService matchingService;

    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_create_job",
            description = "Create a new job posting as recruiter. Input is job JSON payload.")
    @Authenticated
    public TextContent createJob(String jobJson) {
        try {
            if (jobJson == null || jobJson.isBlank()) {
                return new TextContent("Error: jobJson is required.");
            }
            if (jobJson.length() > MAX_PAYLOAD_SIZE) {
                return new TextContent("Error: job payload exceeds maximum size.");
            }
            UUID userId = userResolver.resolveUserId();
            JobPostingPayload payload = objectMapper.readValue(jobJson, JobPostingPayload.class);
            JobPostingDto created = jobService.createJob(userId, payload);
            return new TextContent("Job created with id " + created.id() + " in status " + created.status() + ".");
        } catch (Exception e) {
            LOG.error("Failed to create job", e);
            return new TextContent("Failed to create job: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_list_my_jobs",
            description = "List jobs owned by the authenticated recruiter.")
    @Authenticated
    public TextContent listMyJobs() {
        try {
            UUID userId = userResolver.resolveUserId();
            List<JobPostingDto> jobs = jobService.listJobs(userId);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobs);
            return new TextContent(json);
        } catch (Exception e) {
            LOG.error("Failed to list jobs", e);
            return new TextContent("Failed to list jobs.");
        }
    }

    @Tool(name = "peoplemesh_update_job",
            description = "Update an existing job posting. Provide jobId and jobJson payload.")
    @Authenticated
    public TextContent updateJob(String jobId, String jobJson) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            if (jobJson == null || jobJson.isBlank()) {
                return new TextContent("Error: jobJson is required.");
            }
            UUID userId = userResolver.resolveUserId();
            JobPostingPayload payload = objectMapper.readValue(jobJson, JobPostingPayload.class);
            return jobService.updateJob(userId, UUID.fromString(jobId), payload)
                    .map(job -> new TextContent("Job updated: " + job.id() + " (" + job.status() + ")"))
                    .orElse(new TextContent("Job not found."));
        } catch (Exception e) {
            LOG.error("Failed to update job", e);
            return new TextContent("Failed to update job: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_transition_job_status",
            description = "Change job lifecycle status. Valid values: DRAFT, PUBLISHED, PAUSED, FILLED, CLOSED.")
    @Authenticated
    public TextContent transitionJobStatus(String jobId, String status) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            if (status == null || status.isBlank()) {
                return new TextContent("Error: status is required.");
            }
            UUID userId = userResolver.resolveUserId();
            JobStatus target = JobStatus.valueOf(status.toUpperCase());
            return jobService.transitionStatus(userId, UUID.fromString(jobId), target)
                    .map(job -> new TextContent("Job status changed to " + job.status() + "."))
                    .orElse(new TextContent("Job not found."));
        } catch (Exception e) {
            LOG.error("Failed to transition job status", e);
            return new TextContent("Failed to transition job status: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_find_job_matches",
            description = "Find relevant published jobs for the authenticated candidate. Optional filters JSON uses MatchFilters fields.")
    @Authenticated
    public TextContent findJobMatches(String filtersJson) {
        try {
            UUID userId = userResolver.resolveUserId();
            MatchFilters filters = parseFilters(filtersJson);
            List<JobMatchResult> matches = matchingService.findJobMatches(userId, filters);
            if (matches.isEmpty()) {
                return new TextContent("No job matches found.");
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matches);
            return new TextContent("Found " + matches.size() + " job matches:\n\n" + json);
        } catch (Exception e) {
            LOG.error("Failed to find job matches", e);
            return new TextContent("Failed to find job matches: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_find_candidates_for_job",
            description = "Find matching candidates for one of your jobs. Provide jobId and optional MatchFilters JSON.")
    @Authenticated
    public TextContent findCandidatesForJob(String jobId, String filtersJson) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            UUID userId = userResolver.resolveUserId();
            MatchFilters filters = parseFilters(filtersJson);
            List<MatchResult> matches = matchingService.findCandidatesForJob(userId, UUID.fromString(jobId), filters);
            if (matches.isEmpty()) {
                return new TextContent("No matching candidates found.");
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matches);
            return new TextContent("Found " + matches.size() + " candidate matches:\n\n" + json);
        } catch (Exception e) {
            LOG.error("Failed to find candidates for job", e);
            return new TextContent("Failed to find candidates for job: " + e.getMessage());
        }
    }

    private MatchFilters parseFilters(String filtersJson) throws Exception {
        if (filtersJson == null || filtersJson.isBlank()) {
            return null;
        }
        if (filtersJson.length() > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("filters payload exceeds maximum size");
        }
        return objectMapper.readValue(filtersJson, MatchFilters.class);
    }
}
