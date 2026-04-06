package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.CandidatePipelineDto;
import org.peoplemesh.domain.dto.CandidatePipelineUpdate;
import org.peoplemesh.domain.enums.PipelineStage;
import org.peoplemesh.service.RecruiterPipelineService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class RecruiterTools {

    private static final Logger LOG = Logger.getLogger(RecruiterTools.class);
    private static final int MAX_PAYLOAD_SIZE = 16 * 1024;

    @Inject
    UserResolver userResolver;

    @Inject
    RecruiterPipelineService pipelineService;

    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_add_candidate_to_pipeline",
            description = "Add or upsert a candidate profile into a job pipeline. Provide jobId, targetProfileId, and optional updateJson with stage/shortlisted/notes.")
    @Authenticated
    public TextContent addCandidateToPipeline(String jobId, String targetProfileId, String updateJson) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            if (targetProfileId == null || targetProfileId.isBlank()) {
                return new TextContent("Error: targetProfileId is required.");
            }
            UUID userId = userResolver.resolveUserId();
            CandidatePipelineUpdate update = parseUpdate(updateJson);
            CandidatePipelineDto entry = pipelineService.addCandidate(
                    userId, UUID.fromString(jobId), UUID.fromString(targetProfileId), update);
            return new TextContent("Candidate added to pipeline with stage " + entry.stage() + ".");
        } catch (Exception e) {
            LOG.error("Failed to add candidate to pipeline", e);
            return new TextContent("Failed to add candidate to pipeline: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_update_pipeline_candidate",
            description = "Update stage, shortlist flag, or notes for a candidate in a job pipeline.")
    @Authenticated
    public TextContent updatePipelineCandidate(String jobId, String candidateUserId, String updateJson) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            if (candidateUserId == null || candidateUserId.isBlank()) {
                return new TextContent("Error: candidateUserId is required.");
            }
            UUID userId = userResolver.resolveUserId();
            CandidatePipelineUpdate update = parseUpdate(updateJson);
            return pipelineService.updateCandidate(
                            userId, UUID.fromString(jobId), UUID.fromString(candidateUserId), update)
                    .map(e -> new TextContent("Pipeline candidate updated to stage " + e.stage() + "."))
                    .orElse(new TextContent("Pipeline entry not found."));
        } catch (Exception e) {
            LOG.error("Failed to update pipeline candidate", e);
            return new TextContent("Failed to update pipeline candidate: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_get_job_pipeline",
            description = "List candidate pipeline entries for a job. Optional stage and shortlistedOnly parameters.")
    @Authenticated
    public TextContent getJobPipeline(String jobId, String stage, boolean shortlistedOnly) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            UUID userId = userResolver.resolveUserId();
            PipelineStage parsed = null;
            if (stage != null && !stage.isBlank()) {
                parsed = PipelineStage.valueOf(stage.toUpperCase());
            }
            List<CandidatePipelineDto> entries = pipelineService.listPipeline(
                    userId, UUID.fromString(jobId), parsed, shortlistedOnly);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
            return new TextContent(json);
        } catch (Exception e) {
            LOG.error("Failed to list job pipeline", e);
            return new TextContent("Failed to list job pipeline: " + e.getMessage());
        }
    }

    @Tool(name = "peoplemesh_get_job_inbox",
            description = "List inbox candidates (stage APPLIED) for a job.")
    @Authenticated
    public TextContent getJobInbox(String jobId) {
        try {
            if (jobId == null || jobId.isBlank()) {
                return new TextContent("Error: jobId is required.");
            }
            UUID userId = userResolver.resolveUserId();
            List<CandidatePipelineDto> entries = pipelineService.listInbox(userId, UUID.fromString(jobId));
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
            return new TextContent(json);
        } catch (Exception e) {
            LOG.error("Failed to list job inbox", e);
            return new TextContent("Failed to list job inbox: " + e.getMessage());
        }
    }

    private CandidatePipelineUpdate parseUpdate(String updateJson) throws Exception {
        if (updateJson == null || updateJson.isBlank()) {
            return new CandidatePipelineUpdate(null, null, null);
        }
        if (updateJson.length() > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("update payload exceeds maximum size");
        }
        return objectMapper.readValue(updateJson, CandidatePipelineUpdate.class);
    }
}
