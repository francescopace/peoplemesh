package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.service.MatchesService;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.ProfileService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class McpReadTools {

    private static final Logger LOG = Logger.getLogger(McpReadTools.class);
    private static final int MAX_QUERY_LENGTH = 500;

    @Inject
    CurrentUserService currentUserService;
    @Inject
    ProfileService profileService;
    @Inject
    MatchesService matchesService;
    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_get_my_profile",
          description = "Retrieve the authenticated user's PeopleMesh profile as JSON. Use this when profile data is explicitly needed. Treat missing fields as unknown and do not infer facts that are not present in the profile.")
    @Authenticated
    @SuppressWarnings("null")
    public TextContent getMyProfile() {
        try {
            UUID userId = currentUserService.resolveUserId();
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
            return new TextContent("Error: access denied.");
        } catch (Exception e) {
            LOG.errorf(e, "Error retrieving profile");
            return McpToolHelper.error("retrieve your profile");
        }
    }

    @Tool(name = "peoplemesh_match_prompt",
          description = "Search PeopleMesh from a natural-language query using the server-side query parser and ranking logic. Provide the user's request verbatim in query. Do not rewrite, broaden, relax, or reinterpret the criteria unless the user explicitly asks. Never set country automatically from locale, profile, session, or prior context; leave it empty unless the user explicitly requested a country or location filter. Use type only when the user clearly requested a result category such as PEOPLE, JOB, COMMUNITY, EVENT, PROJECT, or INTEREST_GROUP. Returns JSON with both the parsedQuery and the ranked results.")
    @Authenticated
    public TextContent matchPrompt(String query, String type, String country) {
        try {
            if (query == null || query.isBlank()) {
                return new TextContent("Error: query is required.");
            }
            if (query.length() > MAX_QUERY_LENGTH) {
                return new TextContent("Error: query exceeds maximum length.");
            }
            UUID userId = currentUserService.resolveUserId();
            SearchResponse response = matchesService.matchFromPrompt(
                    userId,
                    new SearchRequest(query.trim()),
                    type,
                    country,
                    null
            );
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            return new TextContent(json);
        } catch (SecurityException e) {
            return new TextContent("Error: access denied.");
        } catch (BusinessException e) {
            return new TextContent("Error: " + e.publicDetail());
        } catch (Exception e) {
            LOG.errorf(e, "Error running match from prompt");
            return McpToolHelper.error("match from prompt");
        }
    }

    @Tool(name = "peoplemesh_match_me",
          description = "Find matches using the authenticated user's existing PeopleMesh profile as the source. Use this for requests like 'match my profile' or 'find opportunities for me'. Do not use it as a substitute for a new natural-language search request. Apply optional type and country filters only when the user explicitly requested them.")
    @Authenticated
    public TextContent matchMe(String type, String country) {
        try {
            UUID userId = currentUserService.resolveUserId();
            List<MeshMatchResult> results = matchesService.matchMyProfile(userId, type, country);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
            return new TextContent(json);
        } catch (SecurityException e) {
            return new TextContent("Error: access denied.");
        } catch (BusinessException e) {
            return new TextContent("Error: " + e.publicDetail());
        } catch (Exception e) {
            LOG.errorf(e, "Error finding matches for your profile");
            return McpToolHelper.error("find matches for your profile");
        }
    }
}
