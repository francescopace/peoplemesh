package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.util.Optional;

@ApplicationScoped
public class LlmProfileStructuring implements ProfileStructuringLlm {

    private static final Logger LOG = Logger.getLogger(LlmProfileStructuring.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert HR assistant. Parse the candidate CV (Markdown/text) and output ONE valid JSON object.
            The JSON MUST match this exact structure and key names:
            {
              "profile_version": "1.0",
              "generated_at": "<ISO-8601 timestamp>",
              "professional": {
                "roles": ["<CURRENT role only, exactly one element>"],
                "seniority": "JUNIOR|MID|SENIOR|LEAD|EXECUTIVE",
                "industries": ["..."],
                "skills_technical": ["..."],
                "skills_soft": ["..."],
                "tools_and_tech": ["..."],
                "languages_spoken": ["..."],
                "work_mode_preference": "REMOTE|HYBRID|OFFICE|FLEXIBLE|null",
                "employment_type": "EMPLOYED|FREELANCE|FOUNDER|LOOKING|OPEN_TO_OFFERS|null"
              },
              "interests_professional": {
                "topics_frequent": ["..."],
                "learning_areas": ["..."],
                "project_types": ["..."]
              },
              "geography": {
                "country": "<ISO country code if clear, else null>",
                "city": "<city if clear, else null>",
                "timezone": null
              },
              "field_provenance": {
                "professional.roles": "cv_llm",
                "professional.skills_technical": "cv_llm",
                "professional.languages_spoken": "cv_llm",
                "geography.country": "cv_llm"
              }
            }
            Rules:
            - Output ONLY raw JSON, no markdown fences.
            - Never return {} if CV has meaningful text.
            - Fill as many fields as possible from evidence in CV.
            - If a field is unknown, use null (not empty strings).
            - "roles" must contain exactly ONE string: the candidate's current/most-recent job title.
            """;

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
    public Optional<ProfileSchema> extractProfile(String cvContent) {
        int cvChars = cvContent != null ? cvContent.length() : 0;
        int cvWords = (cvContent == null || cvContent.isBlank()) ? 0 : cvContent.trim().split("\\s+").length;
        LOG.infof("Profile structuring LLM started: chars=%d words=%d", cvChars, cvWords);

        String content;
        try {
            content = chatModel.chat(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("CV Content:\n" + cvContent)
            ).aiMessage().text();
        } catch (Exception e) {
            LOG.errorf(e, "LLM call failed for CV extraction");
            return Optional.empty();
        }

        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        try {
            LOG.debugf("Profile structuring LLM response: responseLength=%d", content.length());
            ProfileSchema extracted = objectMapper.readValue(MatchingUtils.stripMarkdownFences(content), ProfileSchema.class);
            logExtractionSummary(extracted);
            return Optional.ofNullable(extracted);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse LLM response for CV extraction");
            return Optional.empty();
        }
    }

    private void logExtractionSummary(ProfileSchema schema) {
        if (schema == null) {
            LOG.warn("Profile structuring LLM extraction produced null schema");
            return;
        }
        var p = schema.professional();
        var g = schema.geography();
        int roles = p != null && p.roles() != null ? p.roles().size() : 0;
        int industries = p != null && p.industries() != null ? p.industries().size() : 0;
        int skillsTech = p != null && p.skillsTechnical() != null ? p.skillsTechnical().size() : 0;
        int skillsSoft = p != null && p.skillsSoft() != null ? p.skillsSoft().size() : 0;
        int tools = p != null && p.toolsAndTech() != null ? p.toolsAndTech().size() : 0;
        int langs = p != null && p.languagesSpoken() != null ? p.languagesSpoken().size() : 0;
        String seniority = p != null && p.seniority() != null ? p.seniority().name() : "null";
        String country = g != null ? g.country() : null;
        String city = g != null ? g.city() : null;

        LOG.infof(
                "Profile structuring LLM extraction summary: roles=%d industries=%d skillsTech=%d skillsSoft=%d tools=%d langs=%d seniority=%s country=%s city=%s",
                roles, industries, skillsTech, skillsSoft, tools, langs, seniority, country, city
        );
    }

}
