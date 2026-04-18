package org.peoplemesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ClusterName;
import org.peoplemesh.util.ClusterNamingHeuristics;
import org.peoplemesh.util.StringUtils;

import java.util.*;

@ApplicationScoped
public class LlmClusterNaming implements ClusterNamingLlm {

    private static final Logger LOG = Logger.getLogger(LlmClusterNaming.class);

    private static final String SYSTEM_PROMPT = """
            You are a community naming assistant. Given traits of a group of people (their skills, hobbies, sports, causes, topics, and countries), generate a community name and description.
            Output ONE valid JSON object with keys: "title" (short catchy name, max 60 chars), "description" (2-3 sentences), "tags" (array of 3-7 keywords).
            Do NOT include any text outside the JSON object.""";

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Timed(
            value = "peoplemesh.llm.inference",
            description = "LLM inference latency",
            percentiles = {0.95},
            histogram = true
    )
    public Optional<ClusterName> generateName(Map<String, List<String>> traits) {
        String content;
        try {
            content = chatModel.chat(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("Cluster traits:\n" + formatTraits(traits))
            ).aiMessage().text();
        } catch (Exception e) {
            LOG.warnf(e, "LLM call failed, falling back to heuristic naming");
            return heuristicName(traits);
        }

        if (content == null || content.isBlank()) {
            LOG.warn("LLM returned empty response, falling back to heuristic naming");
            return heuristicName(traits);
        }

        try {
            JsonNode parsed = objectMapper.readTree(StringUtils.stripMarkdownFences(content));

            String title = parsed.path("title").asText();
            String description = parsed.path("description").asText();
            List<String> tags = new ArrayList<>();
            parsed.path("tags").forEach(t -> tags.add(t.asText()));

            if (title.isBlank() || description.isBlank()) {
                return heuristicName(traits);
            }
            return Optional.of(new ClusterName(title, description, tags));
        } catch (Exception e) {
            LOG.warnf(e, "LLM naming parsing failed, falling back to heuristic");
            return heuristicName(traits);
        }
    }

    private Optional<ClusterName> heuristicName(Map<String, List<String>> traits) {
        return ClusterNamingHeuristics.fromTraits(traits);
    }

    private String formatTraits(Map<String, List<String>> traits) {
        StringBuilder sb = new StringBuilder();
        for (var entry : traits.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                Set<String> unique = new LinkedHashSet<>(entry.getValue());
                sb.append("- ").append(entry.getKey()).append(": ").append(String.join(", ", unique)).append("\n");
            }
        }
        return sb.toString();
    }
}
