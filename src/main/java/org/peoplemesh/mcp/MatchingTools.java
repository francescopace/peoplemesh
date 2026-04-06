package org.peoplemesh.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MatchResult;
import org.peoplemesh.domain.model.UserProfile;
import org.peoplemesh.service.MatchingService;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class MatchingTools {

    private static final Logger LOG = Logger.getLogger(MatchingTools.class);
    private static final int MAX_FILTERS_SIZE = 4 * 1024;

    @Inject
    UserResolver userResolver;

    @Inject
    MatchingService matchingService;

    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_find_matches",
          description = "Find professionals with similar skills, interests, and career goals. " +
                        "Optionally provide filters as JSON with fields: skillsTechnical, collaborationGoals, workMode, employmentType, country.")
    @Authenticated
    public TextContent findMatches(String filtersJson) {
        try {
            UUID userId = userResolver.resolveUserId();
            UserProfile myProfile = UserProfile.findActiveByUserId(userId).orElse(null);
            if (myProfile == null || myProfile.embedding == null) {
                return new TextContent("You need to submit a profile first using peoplemesh_submit_profile.");
            }

            MatchFilters filters = null;
            if (filtersJson != null && !filtersJson.isBlank()) {
                if (filtersJson.length() > MAX_FILTERS_SIZE) {
                    return new TextContent("Error: filters payload exceeds maximum size.");
                }
                filters = objectMapper.readValue(filtersJson, MatchFilters.class);
            }

            List<MatchResult> matches = matchingService.findMatches(userId, myProfile.embedding, filters);

            if (matches.isEmpty()) {
                return new TextContent("No matches found yet. As more professionals join, you'll see relevant connections here.");
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matches);
            return new TextContent("Found " + matches.size() + " matches:\n\n" + json);
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error searching for matches", e);
            return new TextContent("Error searching for matches. Please try again later.");
        }
    }
}
