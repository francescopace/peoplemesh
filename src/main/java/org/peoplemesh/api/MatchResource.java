package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MatchResult;
import org.peoplemesh.domain.enums.CollaborationGoal;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.UserProfile;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.MatchingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/api/v1/matches")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MatchResource {

    @Inject
    UserResolver userResolver;

    @Inject
    MatchingService matchingService;

    @GET
    public Response getMatches(
            @QueryParam("country") String country,
            @QueryParam("workMode") String workMode,
            @QueryParam("employmentType") String employmentType,
            @QueryParam("skills") String skills,
            @QueryParam("goals") String goals) {
        UUID userId = userResolver.resolveUserId();
        UserProfile profile = UserProfile.findActiveByUserId(userId).orElse(null);
        if (profile == null || profile.embedding == null) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", "Submit a profile first"))
                    .build();
        }

        MatchFilters filters;
        try {
            filters = buildFilters(country, workMode, employmentType, skills, goals);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", e.getMessage()))
                    .build();
        }

        List<MatchResult> matches = matchingService.findMatches(userId, profile.embedding, filters);
        return Response.ok(matches).build();
    }

    private MatchFilters buildFilters(String country, String workMode, String employmentType, String skills, String goals) {
        WorkMode parsedWorkMode = null;
        if (workMode != null && !workMode.isBlank()) {
            try {
                parsedWorkMode = WorkMode.valueOf(workMode.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid workMode value: " + workMode);
            }
        }

        EmploymentType parsedEmploymentType = null;
        if (employmentType != null && !employmentType.isBlank()) {
            try {
                parsedEmploymentType = EmploymentType.valueOf(employmentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid employmentType value: " + employmentType);
            }
        }

        List<String> parsedSkills = parseCsv(skills);
        List<CollaborationGoal> parsedGoals = parseCsv(goals).stream()
                .map(g -> CollaborationGoal.valueOf(g.toUpperCase()))
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
