package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.CandidatePipelineDto;
import org.peoplemesh.domain.dto.CandidatePipelineUpdate;
import org.peoplemesh.domain.enums.PipelineStage;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.RecruiterPipelineService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/jobs/{jobId}/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class RecruiterPipelineResource {

    @Inject
    UserResolver userResolver;

    @Inject
    RecruiterPipelineService pipelineService;

    @GET
    public Response listPipeline(
            @PathParam("jobId") UUID jobId,
            @QueryParam("stage") String stage,
            @QueryParam("shortlistedOnly") @DefaultValue("false") boolean shortlistedOnly) {
        try {
            UUID userId = userResolver.resolveUserId();
            PipelineStage parsed = parseStage(stage);
            List<CandidatePipelineDto> entries = pipelineService.listPipeline(userId, jobId, parsed, shortlistedOnly);
            return Response.ok(entries).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @GET
    @Path("/inbox")
    public Response listInbox(@PathParam("jobId") UUID jobId) {
        try {
            UUID userId = userResolver.resolveUserId();
            List<CandidatePipelineDto> entries = pipelineService.listInbox(userId, jobId);
            return Response.ok(entries).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addCandidate(@PathParam("jobId") UUID jobId, Map<String, Object> body) {
        try {
            UUID userId = userResolver.resolveUserId();
            String targetProfileId = body == null ? null : (String) body.get("target_profile_id");
            if (targetProfileId == null || targetProfileId.isBlank()) {
                return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", "target_profile_id is required")).build();
            }
            CandidatePipelineUpdate update = new CandidatePipelineUpdate(
                    parseStage((String) body.get("stage")),
                    body.get("shortlisted") instanceof Boolean b ? b : null,
                    body.get("notes") instanceof String s ? s : null
            );
            CandidatePipelineDto entry = pipelineService.addCandidate(userId, jobId, UUID.fromString(targetProfileId), update);
            return Response.status(Response.Status.CREATED).entity(entry).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{candidateUserId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCandidate(
            @PathParam("jobId") UUID jobId,
            @PathParam("candidateUserId") UUID candidateUserId,
            CandidatePipelineUpdate update) {
        try {
            UUID userId = userResolver.resolveUserId();
            return pipelineService.updateCandidate(userId, jobId, candidateUserId, update)
                    .map(Response::ok)
                    .orElseGet(() -> Response.status(404))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(ProblemDetail.of(400, "Bad Request", e.getMessage())).build();
        }
    }

    private PipelineStage parseStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PipelineStage.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid stage value: " + value);
        }
    }
}
