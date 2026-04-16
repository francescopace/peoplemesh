package org.peoplemesh.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ParsedSearchQuery;

import java.util.Optional;

@ApplicationScoped
public class LlmSearchQueryParser implements SearchQueryParser {

    private static final Logger LOG = Logger.getLogger(LlmSearchQueryParser.class);

    private static final String SYSTEM_PROMPT = """
            Extract search criteria from the query into JSON with this schema:
            {"must_have":{"skills":[],"skills_with_level":[],"languages":[],"industries":[]},\
            "nice_to_have":{"skills":[],"skills_with_level":[],"industries":[]},\
            "seniority":"junior|mid|senior|lead|unknown",\
            "keywords":[],"embedding_text":""}
            Rules:
            - Do not invent facts absent from the query.
            - must_have = strict requirements; nice_to_have = preferences.
            - Normalize aliases (k8s->Kubernetes, js->JavaScript, ts->TypeScript).
            - seniority: only if clearly implied, else "unknown".
            - skills_with_level format: {"name":"X","level":1-5}. Use only when explicit level signal exists.
            - keywords: short deduplicated intent terms.
            - embedding_text: max 10-word English phrase describing the ideal candidate.""";

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
    public Optional<ParsedSearchQuery> parse(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return Optional.empty();
        }

        LOG.debugf("Parsing search query via LLM: queryLength=%d", userQuery.length());

        String content;
        try {
            content = chatModel.chat(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("User query:\n\"" + userQuery + "\"")
            ).aiMessage().text();
        } catch (Exception e) {
            LOG.warnf("LLM call failed for search query parsing: %s", e.getMessage());
            return Optional.empty();
        }

        if (content == null || content.isBlank()) {
            LOG.warn("LLM returned empty response for search query parsing");
            return Optional.empty();
        }

        try {
            ObjectMapper lenient = objectMapper.copy()
                    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                    .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                    .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
            ParsedSearchQuery parsed = lenient.readValue(MatchingUtils.stripMarkdownFences(content), ParsedSearchQuery.class);
            LOG.debugf("Parsed search query: skills=%s, seniority=%s",
                    parsed.mustHave() != null ? parsed.mustHave().skills() : "[]",
                    parsed.seniority());
            return Optional.ofNullable(parsed);
        } catch (Exception e) {
            LOG.warnf("Failed to parse LLM response: %s", e.getMessage());
            return Optional.empty();
        }
    }
}
