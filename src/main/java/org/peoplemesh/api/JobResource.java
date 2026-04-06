package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.JobMatchResult;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.dto.JobPostingPayload;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MatchResult;
import org.peoplemesh.domain.enums.CollaborationGoal;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.JobService;
import org.peoplemesh.service.MatchingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/api/v1/jobs")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class JobResource {

    @Inject
    UserResolver userResolver;

    @Inject
    JobService jobService;

    @Inject
    MatchingService matchingService;

    @GET
    public Response listMyJobs() {
        UUID userId = userResolver.resolveUserId();
        List<JobPostingDto> jobs = jobService.listJobs(userId);
        return Response.ok(jobs).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createJob(JobPostingPayload payload) {
        try {
            UUID userId = userResolver.resolveUserId();
            JobPostingDto created = jobService.createJob(userId, payload);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{jobId}")
    public Response getJob(@PathParam("jobId") UUID jobId) {
        UUID userId = userResolver.resolveUserId();
        return jobService.getJob(userId, jobId)
                .map(Response::ok)
                .orElseGet(() -> Response.status(404))
                .build();
    }

    @PUT
    @Path("/{jobId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateJob(@PathParam("jobId") UUID jobId, JobPostingPayload payload) {
        try {
            UUID userId = userResolver.resolveUserId();
            return jobService.updateJob(userId, jobId, payload)
                    .map(Response::ok)
                    .orElseGet(() -> Response.status(404))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{jobId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response transitionStatus(@PathParam("jobId") UUID jobId, Map<String, String> body) {
        try {
            UUID userId = userResolver.resolveUserId();
            String statusValue = body != null ? body.get("status") : null;
            if (statusValue == null || statusValue.isBlank()) {
                return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", "status is required")).build();
            }
            JobStatus target = JobStatus.valueOf(statusValue.toUpperCase());
            return jobService.transitionStatus(userId, jobId, target)
                    .map(Response::ok)
                    .orElseGet(() -> Response.status(404))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(ProblemDetail.of(409, "Conflict", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @GET
    @Path("/matches")
    public Response findJobMatches(
            @QueryParam("country") String country,
            @QueryParam("workMode") String workMode,
            @QueryParam("employmentType") String employmentType,
            @QueryParam("skills") String skills,
            @QueryParam("goals") String goals) {
        try {
            UUID userId = userResolver.resolveUserId();
            MatchFilters filters = buildFilters(country, workMode, employmentType, skills, goals);
            List<JobMatchResult> matches = matchingService.findJobMatches(userId, filters);
            return Response.ok(matches).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{jobId}/candidates")
    public Response findCandidatesForJob(
            @PathParam("jobId") UUID jobId,
            @QueryParam("country") String country,
            @QueryParam("workMode") String workMode,
            @QueryParam("employmentType") String employmentType,
            @QueryParam("skills") String skills,
            @QueryParam("goals") String goals) {
        try {
            UUID userId = userResolver.resolveUserId();
            MatchFilters filters = buildFilters(country, workMode, employmentType, skills, goals);
            List<MatchResult> matches = matchingService.findCandidatesForJob(userId, jobId, filters);
            return Response.ok(matches).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    private MatchFilters buildFilters(String country, String workMode, String employmentType, String skills, String goals) {
        WorkMode parsedWorkMode = parseEnum(workMode, WorkMode.class, "workMode");
        EmploymentType parsedEmploymentType = parseEnum(employmentType, EmploymentType.class, "employmentType");
        List<String> parsedSkills = parseCsv(skills);
        List<CollaborationGoal> parsedGoals = parseCsv(goals).stream()
                .map(g -> parseEnum(g, CollaborationGoal.class, "goals"))
                .collect(Collectors.toList());

        if (parsedSkills.isEmpty() && parsedGoals.isEmpty() && parsedWorkMode == null
                && parsedEmploymentType == null && (country == null || country.isBlank())) {
            return null;
        }

        return new MatchFilters(
                parsedSkills.isEmpty() ? null : parsedSkills,
                parsedGoals.isEmpty() ? null : parsedGoals,
                parsedWorkMode,
                parsedEmploymentType,
                country
        );
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field + " value: " + value);
        }
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
