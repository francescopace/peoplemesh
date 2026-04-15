package org.peoplemesh.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ParsedSearchQuery;

import java.util.Optional;

@ApplicationScoped
public class LlmSearchQueryParser implements SearchQueryParser {

    private static final Logger LOG = Logger.getLogger(LlmSearchQueryParser.class);

    private static final String SYSTEM_PROMPT = """
            You are an AI system that transforms a natural language search query into a structured professional profile for semantic matching.

            Your task:
            Extract structured information and build a JSON profile optimized for matching people.

            Rules:
            - Be precise and conservative (do NOT hallucinate missing info)
            - Normalize skills and technologies (e.g. k8s -> Kubernetes, openshift -> OpenShift)
            - Infer seniority if possible
            - Separate "must-have" vs "nice-to-have"
            - Detect language and location constraints
            - Detect negative filters (things the user does NOT want)
            - Keep output compact
            - Output ONLY valid JSON, no explanations

            Output JSON schema:
            {
              "must_have": {
                "skills": [],
                "roles": [],
                "languages": [],
                "location": [],
                "industries": []
              },
              "nice_to_have": {
                "skills": [],
                "industries": [],
                "experience": []
              },
              "seniority": "junior | mid | senior | lead | unknown",
              "negative_filters": {
                "seniority": null,
                "skills": [],
                "location": null
              },
              "keywords": [],
              "embedding_text": ""
            }

            Important:
            - "embedding_text" must be a clean natural sentence representing the ideal candidate
            - Always generate "embedding_text" in English, regardless of the input language
            - Output ONLY JSON""";

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @Override
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
