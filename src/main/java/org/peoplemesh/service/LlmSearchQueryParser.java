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
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class LlmSearchQueryParser implements SearchQueryParser {

    private static final Logger LOG = Logger.getLogger(LlmSearchQueryParser.class);
    private static final Set<String> ALLOWED_SENIORITY = Set.of("junior", "mid", "senior", "lead", "unknown");
    private static final Set<String> ALLOWED_RESULT_SCOPE = Set.of(
            "all", "people", "jobs", "communities", "events", "projects", "groups", "unknown"
    );

    private static final String SYSTEM_PROMPT = """
            Extract search criteria from the user query.
            Output ONLY one raw JSON object (no markdown, no comments) using this exact schema:
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
              "embedding_text": "",
              "result_scope": "all|people|jobs|communities|events|projects|groups|unknown"
            }
            Rules:
            - Return ALL keys from the schema every time; use [] or null when unknown.
            - Do not invent facts absent from the query.
            - must_have = strict requirements; nice_to_have = preferences.
            - skills = technologies, methods, or professional domains (Java, Kubernetes, machine learning).
            - roles = job titles only (developer, architect, data engineer).
            - languages = spoken human languages only (English, Italian). Never technologies.
            - location = geographic places (city, country, region, continent: Europe, Italy, Berlin).
            - industries = business sectors only (finance, healthcare, telecom, e-commerce).
            - Normalize aliases only when explicit: k8s->Kubernetes, js->JavaScript, ts->TypeScript, ml->machine learning, ai->AI.
            - skills_with_level item format is {"name":"X","min_level":1-5}; use only with explicit level signals.
            - seniority must be set only when clearly implied, else "unknown".
            - For queries about open roles/jobs/opportunities, keep seniority as "unknown" unless explicit seniority words appear.
            - negative_filters only for explicit exclusions (e.g., "not junior", "exclude Germany").
            - keywords must be short deduplicated intent terms useful for non-profile nodes (events, community, jobs).
            - embedding_text must be an English phrase of max 10 words describing user intent.
            - result_scope must be one of: all, people, jobs, communities, events, projects, groups, unknown.
            - If query implies "all/tutti/everything/any type", set result_scope = "all".
            - If query asks for communities, set result_scope = "communities".
            - If query asks for events/meetups/conferences, set result_scope = "events".
            - If query asks for jobs/open roles/opportunities, set result_scope = "jobs".
            - If query asks for projects, set result_scope = "projects".
            - If query asks for groups, set result_scope = "groups".
            - If query is a skill+role search with no all-signal, set result_scope = "people".
            - If intent is unclear, set result_scope = "unknown".

            Examples:
            Query: "Java developer with Kubernetes experience"
            JSON: {"must_have":{"skills":["Java","Kubernetes"],"skills_with_level":[],"roles":["developer"],"languages":[],"location":[],"industries":[]},"nice_to_have":{"skills":[],"skills_with_level":[],"industries":[],"experience":[]},"seniority":"unknown","negative_filters":{"seniority":null,"skills":[],"location":[]},"keywords":["developer"],"embedding_text":"Java developer with Kubernetes","result_scope":"people"}

            Query: "Community for data engineers in Europe"
            JSON: {"must_have":{"skills":["data engineering"],"skills_with_level":[],"roles":["data engineer"],"languages":[],"location":["Europe"],"industries":[]},"nice_to_have":{"skills":[],"skills_with_level":[],"industries":[],"experience":[]},"seniority":"unknown","negative_filters":{"seniority":null,"skills":[],"location":[]},"keywords":["community"],"embedding_text":"Data engineering community in Europe","result_scope":"communities"}

            Query: "Events about AI and machine learning"
            JSON: {"must_have":{"skills":["AI","machine learning"],"skills_with_level":[],"roles":[],"languages":[],"location":[],"industries":[]},"nice_to_have":{"skills":[],"skills_with_level":[],"industries":[],"experience":[]},"seniority":"unknown","negative_filters":{"seniority":null,"skills":[],"location":[]},"keywords":["events"],"embedding_text":"Events about AI and machine learning","result_scope":"events"}

            Query: "Open roles in cloud architecture"
            JSON: {"must_have":{"skills":["cloud architecture"],"skills_with_level":[],"roles":["architect"],"languages":[],"location":[],"industries":[]},"nice_to_have":{"skills":[],"skills_with_level":[],"industries":[],"experience":[]},"seniority":"unknown","negative_filters":{"seniority":null,"skills":[],"location":[]},"keywords":["roles"],"embedding_text":"Open roles in cloud architecture","result_scope":"jobs"}""";

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
    public Optional<SearchQuery> parse(String userQuery) {
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
            LOG.errorf(e, "LLM call failed for search query parsing");
            throw new IllegalStateException("LLM call failed for search query parsing", e);
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned empty response for search query parsing");
        }

        try {
            ObjectMapper lenient = objectMapper.copy()
                    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                    .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                    .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
            SearchQuery parsed = lenient.readValue(StringUtils.stripMarkdownFences(content), SearchQuery.class);
            SearchQuery normalized = normalizeParsedQuery(parsed, userQuery);
            int skillCount = normalized.mustHave() != null && normalized.mustHave().skills() != null
                    ? normalized.mustHave().skills().size()
                    : 0;
            LOG.debugf("Parsed search query successfully: skillCount=%d seniority=%s scope=%s",
                    skillCount,
                    normalized.seniority(),
                    normalized.resultScope());
            return Optional.of(normalized);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse LLM response");
            throw new IllegalStateException("Failed to parse LLM response", e);
        }
    }

    private SearchQuery normalizeParsedQuery(SearchQuery parsed, String userQuery) {
        if (parsed == null) {
            return null;
        }
        SearchQuery.MustHaveFilters mustHave = normalizeMustHave(parsed.mustHave());
        SearchQuery.NiceToHaveFilters niceToHave = normalizeNiceToHave(parsed.niceToHave());
        SearchQuery.NegativeFilters negativeFilters = normalizeNegativeFilters(parsed.negativeFilters());
        List<String> keywords = normalizeStringList(parsed.keywords());
        String embeddingText = parsed.embeddingText() == null || parsed.embeddingText().isBlank()
                ? userQuery
                : parsed.embeddingText().trim();
        String seniority = normalizeSeniority(parsed.seniority());
        String resultScope = normalizeResultScope(parsed.resultScope());
        return new SearchQuery(mustHave, niceToHave, seniority, negativeFilters, keywords, embeddingText, resultScope);
    }

    private SearchQuery.MustHaveFilters normalizeMustHave(SearchQuery.MustHaveFilters source) {
        if (source == null) {
            return new SearchQuery.MustHaveFilters(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        return new SearchQuery.MustHaveFilters(
                normalizeStringList(source.skills()),
                normalizeSkillWithLevelList(source.skillsWithLevel()),
                normalizeStringList(source.roles()),
                normalizeStringList(source.languages()),
                normalizeStringList(source.location()),
                normalizeStringList(source.industries())
        );
    }

    private SearchQuery.NiceToHaveFilters normalizeNiceToHave(SearchQuery.NiceToHaveFilters source) {
        if (source == null) {
            return new SearchQuery.NiceToHaveFilters(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        return new SearchQuery.NiceToHaveFilters(
                normalizeStringList(source.skills()),
                normalizeSkillWithLevelList(source.skillsWithLevel()),
                normalizeStringList(source.industries()),
                normalizeStringList(source.experience())
        );
    }

    private SearchQuery.NegativeFilters normalizeNegativeFilters(SearchQuery.NegativeFilters source) {
        if (source == null) {
            return new SearchQuery.NegativeFilters(null, Collections.emptyList(), Collections.emptyList());
        }
        return new SearchQuery.NegativeFilters(
                source.seniority() == null || source.seniority().isBlank() ? null : source.seniority().trim(),
                normalizeStringList(source.skills()),
                normalizeStringList(source.location())
        );
    }

    private List<SkillWithLevel> normalizeSkillWithLevelList(List<SkillWithLevel> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkillWithLevel> cleaned = source.stream()
                .filter(s -> s != null && s.name() != null && !s.name().isBlank())
                .map(s -> {
                    Integer minLevel = s.minLevel();
                    if (minLevel != null) {
                        minLevel = Math.max(1, Math.min(5, minLevel));
                    }
                    return new SkillWithLevel(s.name().trim(), minLevel);
                })
                .toList();
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }
        return cleaned.stream()
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                swl -> swl.name().toLowerCase(Locale.ROOT),
                                swl -> swl,
                                (left, right) -> right,
                                java.util.LinkedHashMap::new
                        ),
                        m -> List.copyOf(m.values())
                ));
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private String normalizeSeniority(String rawSeniority) {
        if (rawSeniority == null || rawSeniority.isBlank()) {
            return "unknown";
        }
        String normalized = rawSeniority.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SENIORITY.contains(normalized)) {
            return "unknown";
        }
        return normalized;
    }

    private String normalizeResultScope(String rawResultScope) {
        if (rawResultScope == null || rawResultScope.isBlank()) {
            return "unknown";
        }
        String normalized = rawResultScope.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_RESULT_SCOPE.contains(normalized)) {
            return "unknown";
        }
        return normalized;
    }
}
