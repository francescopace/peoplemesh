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
            Convert ONE user search query into ONE JSON object for candidate matching.

            Return ONLY JSON. No markdown, no explanations, no code fences.

            Use this exact schema and keep all keys:
            {
              "must_have": {
                "skills": [],
                "skills_with_level": [],
                "roles": [],
                "languages": [],
                "location": [],
                "industries": []
              },
              "nice_to_have": {
                "skills": [],
                "skills_with_level": [],
                "industries": [],
                "experience": []
              },
              "seniority": "junior|mid|senior|lead|unknown",
              "negative_filters": {
                "seniority": null,
                "skills": [],
                "location": []
              },
              "keywords": [],
              "embedding_text": ""
            }

            Rules:
            - Be conservative: do not invent facts.
            - Put strict requirements in must_have, optional preferences in nice_to_have.
            - Normalize known aliases (k8s->Kubernetes, js->JavaScript, ts->TypeScript).
            - Infer seniority only if clearly implied; otherwise "unknown".
            - If a field is missing, return empty array (or null only for negative_filters.seniority).
            - skills_with_level items format: {"name":"<skill>","level":1-5}. Use only if explicit level signal exists.
            - keywords: short deduplicated terms from query intent.
            - embedding_text: one short English sentence describing the ideal candidate profile.
            - Keep output compact and valid JSON only.""";

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
