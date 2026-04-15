package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.service.EmbeddingService;
import org.peoplemesh.service.EmbeddingTextBuilder;
import org.peoplemesh.service.MatchingService;
import org.peoplemesh.service.ProfileService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class McpReadTools {

    private static final Logger LOG = Logger.getLogger(McpReadTools.class);
    private static final int MAX_PAYLOAD_SIZE = 64 * 1024;

    @Inject
    UserResolver userResolver;
    @Inject
    ProfileService profileService;
    @Inject
    MatchingService matchingService;
    @Inject
    EmbeddingService embeddingService;
    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_get_my_profile",
          description = "Retrieve your current PeopleMesh profile including professional, personal, and interest data.")
    @Authenticated
    @SuppressWarnings("null")
    public TextContent getMyProfile() {
        try {
            UUID userId = userResolver.resolveUserId();
            return profileService.getProfile(userId)
                    .map(schema -> {
                        try {
                            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
                            return new TextContent(json);
                        } catch (Exception e) {
                            LOG.errorf(e, "Error serializing profile");
                            return McpToolHelper.error("retrieve your profile");
                        }
                    })
                    .orElse(new TextContent("No profile found. Create one via the PeopleMesh web UI."));
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error retrieving profile");
            return McpToolHelper.error("retrieve your profile");
        }
    }

    @Tool(name = "peoplemesh_match",
          description = "Find matches using a structured profile. Provide a JSON ProfileSchema with roles, skills, interests, etc. Optionally filter by type (PEOPLE, JOB, COMMUNITY, EVENT, PROJECT, INTEREST_GROUP) and country (ISO code).")
    @Authenticated
    public TextContent match(String profileJson, String type, String country) {
        try {
            if (profileJson == null || profileJson.isBlank()) {
                return new TextContent("Error: profileJson is required.");
            }
            if (profileJson.length() > MAX_PAYLOAD_SIZE) {
                return new TextContent("Error: profile payload exceeds maximum size.");
            }
            ProfileSchema schema = McpToolHelper.parsePayload(
                    profileJson, ProfileSchema.class, MAX_PAYLOAD_SIZE, objectMapper);
            String text = EmbeddingTextBuilder.buildFromSchema(schema);
            float[] embedding = embeddingService.generateEmbedding(text);
            if (embedding == null) {
                return new TextContent("Error: Embedding input was empty.");
            }
            UUID userId = userResolver.resolveUserId();
            List<MeshMatchResult> results = matchingService.findAllMatches(userId, embedding, type, country);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
            return new TextContent(json);
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error running match from profile schema");
            return McpToolHelper.error("match from profile");
        }
    }

    @Tool(name = "peoplemesh_match_me",
          description = "Find matches based on your own profile. Optionally filter by type (PEOPLE, JOB, COMMUNITY, EVENT, PROJECT, INTEREST_GROUP) and country (ISO code).")
    @Authenticated
    public TextContent matchMe(String type, String country) {
        try {
            UUID userId = userResolver.resolveUserId();
            List<MeshMatchResult> results = matchingService.findAllMatches(userId, type, country);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
            return new TextContent(json);
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error finding matches for your profile");
            return McpToolHelper.error("find matches for your profile");
        }
    }

    @Tool(name = "peoplemesh_match_node",
          description = "Find matches based on a specific node's profile. Provide the nodeId (UUID). Optionally filter by type and country.")
    @Authenticated
    public TextContent matchNode(String nodeId, String type, String country) {
        try {
            if (nodeId == null || nodeId.isBlank()) {
                return new TextContent("Error: nodeId is required.");
            }
            UUID id;
            try {
                id = UUID.fromString(nodeId.trim());
            } catch (IllegalArgumentException e) {
                return new TextContent("Error: Invalid nodeId format.");
            }
            MeshNode node = MeshNode.<MeshNode>findByIdOptional(id).orElse(null);
            if (node == null || node.embedding == null) {
                return new TextContent("Node not found or has no embedding.");
            }
            UUID userId = userResolver.resolveUserId();
            boolean isOwner = userId.equals(node.createdBy);
            boolean isPublicNonUser = node.searchable && node.nodeType != NodeType.USER;
            if (!isOwner && !isPublicNonUser) {
                return new TextContent("Error: You do not have access to this node.");
            }
            List<MeshMatchResult> results = matchingService.findAllMatches(userId, node.embedding, type, country);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
            return new TextContent(json);
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error matching from node");
            return McpToolHelper.error("match from node");
        }
    }
}
