package org.peoplemesh.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.util.OAuthProfileParser;
import org.peoplemesh.util.ProfileSchemaNormalization;
import org.peoplemesh.util.ProfileSchemaSanitizer;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class GitHubLlmProfileStructuringService {

    private static final Logger LOG = Logger.getLogger(GitHubLlmProfileStructuringService.class);
    private static final String SOURCE_GITHUB = "github";

    private static final String SYSTEM_PROMPT = """
            You classify GitHub profile import signals into profile fields for professional matching.
            Output ONLY one JSON object with this exact schema:
            {
              "roles": [],
              "seniority": "JUNIOR|MID|SENIOR|LEAD|EXECUTIVE|null",
              "skills_technical": [],
              "tools_and_tech": [],
              "learning_areas": [],
              "project_types": []
            }
            Rules:
            - Use evidence from GitHub bio + repository languages + repository labels only.
            - Prefer precision: if uncertain, leave field empty.
            - roles: include only clear job-title-like values from bio. Do NOT infer from generic bios.
            - seniority: set only when explicit in bio title cues; otherwise null.
            - skills_technical: technologies/frameworks/programming domains.
            - tools_and_tech: concrete tools/platforms (CI, cloud, orchestration, IDE/infra tools).
            - learning_areas: technical topics/goals, not specific products unless clearly topic-like.
            - project_types: domain/project categories (e.g. platform engineering, semantic search, IoT).
            - Exclude noisy/generic labels: docs, demo, sample, tutorial, test, personal, website.
            - Keep lists deduplicated and concise.
            """;

    @Inject
    ChatModel chatModel;

    @Inject
    ObjectMapper objectMapper;

    @Timed(
            value = "peoplemesh.llm.inference",
            description = "LLM inference latency",
            percentiles = {0.95},
            histogram = true
    )
    public ProfileSchema enrichGitHubImport(GitHubEnrichedResult enriched) {
        if (enriched == null) {
            throw new IllegalArgumentException("GitHub enriched payload is required");
        }
        ProfileSchema baseSchema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);
        NormalizedClassification classified = classify(enriched);
        ProfileSchema merged = mergeWithBase(baseSchema, classified);
        return ProfileSchemaSanitizer.sanitizeStructuredSchema(merged);
    }

    private NormalizedClassification classify(GitHubEnrichedResult enriched) {
        String prompt = buildUserPrompt(enriched);
        String content;
        try {
            content = chatModel.chat(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(prompt)
            ).aiMessage().text();
        } catch (Exception e) {
            LOG.errorf(e, "GitHub import LLM classification failed");
            throw new IllegalStateException("GitHub import LLM classification failed", e);
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("GitHub import LLM returned an empty payload");
        }

        try {
            RawClassificationPayload parsed = objectMapper.readValue(
                    StringUtils.stripMarkdownFences(content),
                    RawClassificationPayload.class
            );
            NormalizedClassification normalized = normalizePayload(parsed);
            if (normalized == null) {
                throw new IllegalStateException("GitHub import LLM payload cannot be null");
            }
            return normalized;
        } catch (Exception e) {
            LOG.errorf(e, "GitHub import LLM response parse failed");
            throw new IllegalStateException("GitHub import LLM response parse failed", e);
        }
    }

    private NormalizedClassification normalizePayload(RawClassificationPayload payload) {
        if (payload == null) {
            return null;
        }
        List<String> roles = ProfileSchemaNormalization.normalizeList(
                payload.roles(),
                ProfileSchema.MAX_ROLES,
                ProfileSchema.MAX_ROLE_LENGTH
        );
        List<String> skills = ProfileSchemaNormalization.normalizeList(
                payload.skillsTechnical(),
                ProfileSchema.MAX_SKILLS_TECHNICAL,
                ProfileSchema.MAX_SKILL_TECHNICAL_LENGTH
        );
        List<String> tools = ProfileSchemaNormalization.normalizeList(
                payload.toolsAndTech(),
                ProfileSchema.MAX_TOOLS_AND_TECH,
                ProfileSchema.MAX_TOOL_AND_TECH_LENGTH
        );
        List<String> learning = ProfileSchemaNormalization.normalizeList(
                payload.learningAreas(),
                ProfileSchema.MAX_LEARNING_AREAS,
                ProfileSchema.MAX_LEARNING_AREA_LENGTH
        );
        List<String> projects = ProfileSchemaNormalization.normalizeList(
                payload.projectTypes(),
                ProfileSchema.MAX_PROJECT_TYPES,
                ProfileSchema.MAX_PROJECT_TYPE_LENGTH
        );
        Seniority seniority = parseSeniority(payload.seniority());
        return new NormalizedClassification(roles, seniority, skills, tools, learning, projects);
    }

    private ProfileSchema mergeWithBase(ProfileSchema baseSchema, NormalizedClassification llm) {
        if (baseSchema == null || baseSchema.professional() == null || llm == null) {
            throw new IllegalArgumentException("GitHub schema merge requires non-null base schema and classification");
        }
        ProfileSchema.ProfessionalInfo p = baseSchema.professional();

        List<String> mergedSkills = mergeLists(
                llm.skillsTechnical(),
                p.skillsTechnical(),
                ProfileSchema.MAX_SKILLS_TECHNICAL,
                ProfileSchema.MAX_SKILL_TECHNICAL_LENGTH
        );
        List<String> mergedTools = mergeLists(
                llm.toolsAndTech(),
                p.toolsAndTech(),
                ProfileSchema.MAX_TOOLS_AND_TECH,
                ProfileSchema.MAX_TOOL_AND_TECH_LENGTH
        );
        List<String> roles = llm.roles() != null && !llm.roles().isEmpty() ? llm.roles() : p.roles();
        Seniority seniority = llm.seniority() != null ? llm.seniority() : p.seniority();

        ProfileSchema.ProfessionalInfo mergedProfessional = new ProfileSchema.ProfessionalInfo(
                roles,
                seniority,
                p.industries(),
                mergedSkills,
                p.skillsSoft(),
                mergedTools,
                p.languagesSpoken(),
                p.workModePreference(),
                p.employmentType()
        );

        List<String> learning = llm.learningAreas();
        List<String> projects = llm.projectTypes();
        ProfileSchema.InterestsInfo mergedInterests = (learning == null || learning.isEmpty())
                && (projects == null || projects.isEmpty())
                ? baseSchema.interestsProfessional()
                : new ProfileSchema.InterestsInfo(learning, projects);

        Map<String, String> provenance = baseSchema.fieldProvenance() != null
                ? new LinkedHashMap<>(baseSchema.fieldProvenance())
                : new LinkedHashMap<>();
        if (roles != null && !roles.isEmpty()) provenance.put("professional.roles", SOURCE_GITHUB);
        if (mergedSkills != null && !mergedSkills.isEmpty()) provenance.put("professional.skills_technical", SOURCE_GITHUB);
        if (mergedTools != null && !mergedTools.isEmpty()) provenance.put("professional.tools_and_tech", SOURCE_GITHUB);
        if (learning != null && !learning.isEmpty()) provenance.put("interests_professional.learning_areas", SOURCE_GITHUB);
        if (projects != null && !projects.isEmpty()) provenance.put("interests_professional.project_types", SOURCE_GITHUB);

        return new ProfileSchema(
                baseSchema.profileVersion(),
                baseSchema.generatedAt(),
                baseSchema.consent(),
                mergedProfessional,
                baseSchema.contacts(),
                mergedInterests,
                baseSchema.personal(),
                baseSchema.geography(),
                provenance,
                baseSchema.identity()
        );
    }

    private List<String> mergeLists(List<String> preferred, List<String> fallback, int maxItems, int maxItemLength) {
        List<String> values = new ArrayList<>();
        if (preferred != null) values.addAll(preferred);
        if (fallback != null) values.addAll(fallback);
        return ProfileSchemaNormalization.normalizeList(values, maxItems, maxItemLength);
    }

    private Seniority parseSeniority(String seniority) {
        if (seniority == null || seniority.isBlank()) {
            return null;
        }
        String normalized = seniority.trim().toUpperCase(Locale.ROOT);
        if ("NULL".equals(normalized)) {
            return null;
        }
        return Seniority.valueOf(normalized);
    }

    private String buildUserPrompt(GitHubEnrichedResult enriched) {
        String headline = enriched.subject() != null ? String.valueOf(enriched.subject().headline()) : "";
        List<String> languages = enriched.languages() != null ? enriched.languages() : List.of();
        List<String> labels = enriched.repoLabels() != null ? enriched.repoLabels() : List.of();
        return "GitHub bio/headline:\n" + headline + "\n\n"
                + "Top repository programming languages:\n" + languages + "\n\n"
                + "Repository labels/topics:\n" + labels + "\n\n"
                + "Classify these signals into the requested fields.";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawClassificationPayload(
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("seniority") String seniority,
            @JsonProperty("skills_technical") List<String> skillsTechnical,
            @JsonProperty("tools_and_tech") List<String> toolsAndTech,
            @JsonProperty("learning_areas") List<String> learningAreas,
            @JsonProperty("project_types") List<String> projectTypes
    ) {}

    private record NormalizedClassification(
            List<String> roles,
            Seniority seniority,
            List<String> skillsTechnical,
            List<String> toolsAndTech,
            List<String> learningAreas,
            List<String> projectTypes
    ) {}
}
