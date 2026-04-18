package org.peoplemesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.util.StringUtils;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
              "contacts": {
                "slack_handle": null,
                "telegram_handle": null,
                "mobile_phone": null,
                "linkedin_url": null
              },
              "interests_professional": {
                "topics_frequent": ["..."],
                "learning_areas": ["..."],
                "project_types": ["..."]
              },
              "personal": {
                "hobbies": null,
                "sports": null,
                "education": null,
                "causes": null,
                "personality_tags": null,
                "music_genres": null,
                "book_genres": null
              },
              "geography": {
                "country": "<ISO country code if clear, else null>",
                "city": "<city if clear, else null>",
                "timezone": null
              },
              "identity": {
                "display_name": null,
                "first_name": null,
                "last_name": null,
                "email": null,
                "photo_url": null,
                "company": null,
                "birth_date": null
              },
              "field_provenance": {
                "professional.roles": "cv_llm",
                "professional.skills_technical": "cv_llm",
                "professional.languages_spoken": "cv_llm",
                "contacts.slack_handle": "cv_llm",
                "contacts.telegram_handle": "cv_llm",
                "contacts.mobile_phone": "cv_llm",
                "contacts.linkedin_url": "cv_llm",
                "geography.country": "cv_llm",
                "identity.birth_date": "cv_llm",
                "identity.display_name": "cv_llm",
                "identity.email": "cv_llm"
              }
            }
            Rules:
            - Output ONLY raw JSON, no markdown fences.
            - Never return {} if CV has meaningful text.
            - Use evidence from CV text only. Do not infer facts that are not explicitly supported by the CV.
            - Prefer precision over recall: if uncertain, omit and use null.
            - If a field is unknown, use null (not empty strings).
            - "roles" must contain exactly ONE string and it must be a real job title from EXPERIENCE.
            - Resolve current role deterministically:
              1) If any role has Present/Current/Now in date range, use that role title.
              2) Otherwise use the role with the most recent end date.
              3) Never use summary headlines/descriptors as role values.
            - generated_at must be copied exactly from the input line "Extraction UTC timestamp: ...".
            - work_mode_preference must be null unless CV explicitly states remote/hybrid/office preference.
            - skills_technical must contain atomic technologies (e.g., Kafka, Kubernetes), not section labels (e.g., "Cloud & Infrastructure").
            - skills_soft must contain explicit behavioral capabilities (e.g., public speaking, technical storytelling, team leadership) and must NOT contain business functions (e.g., pre-sales, account management).
            - tools_and_tech must contain only concrete technical tools/platforms/frameworks explicitly named in CV.
            - Exclude from tools_and_tech non-tool entities such as publication channels, events, communities, employers, and role titles.
            - Exclude certifications and credential names from tools_and_tech (e.g., phrases containing "certified" or "certification").
            - learning_areas must include only explicit learning goals/focus areas from CV text; if none are explicit, set learning_areas to null.
            - Populate contacts.* fields when explicitly present in CV: mobile_phone, linkedin_url, telegram_handle, slack_handle.
            - Populate identity fields when explicitly present in CV: display_name, first_name, last_name, email, photo_url, company.
            - Populate personal section when explicitly present (hobbies, sports, education, causes, personality_tags, music_genres, book_genres).
            - birth_date must be YYYY-MM-DD when explicitly present, otherwise null.
            - Keep output compact to avoid truncation: max 8 items for any list field.
            - personal.* fields MUST be arrays of strings or null (never objects).
            - Keep arrays concise, deduplicated, and free of near-duplicates.
            """;

    private static final String REPAIR_PROMPT = """
            You receive malformed or truncated JSON from a previous extraction.
            Return ONE valid JSON object matching the same ProfileSchema structure exactly.
            Rules:
            - Output ONLY raw JSON.
            - Do not add markdown.
            - Keep existing extracted values when possible.
            - If uncertain or missing, use null.
            - Array fields must be arrays of strings (or null), never objects.
            - Keep output compact: max 8 items per array.
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

        String extractionTimestamp = Instant.now().toString();
        String content;
        try {
            content = runChat(
                    SYSTEM_PROMPT,
                    "Extraction UTC timestamp: " + extractionTimestamp + "\nCV Content:\n" + cvContent
            );
        } catch (Exception e) {
            LOG.errorf(e, "LLM call failed for CV extraction");
            return Optional.empty();
        }

        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        Optional<ProfileSchema> parsed = parseProfileSchema(content);
        if (parsed.isPresent()) {
            logExtractionSummary(parsed.get());
            return parsed;
        }

        LOG.warnf("Primary LLM response was invalid or truncated. Attempting JSON repair: responseLength=%d", content.length());
        try {
            String repaired = runChat(
                    REPAIR_PROMPT,
                    "Extraction UTC timestamp: " + extractionTimestamp + "\nMalformed JSON:\n" + StringUtils.stripMarkdownFences(content)
            );
            Optional<ProfileSchema> repairedParsed = parseProfileSchema(repaired);
            if (repairedParsed.isPresent()) {
                logExtractionSummary(repairedParsed.get());
                return repairedParsed;
            }
            LOG.error("Failed to parse repaired LLM response for CV extraction");
        } catch (Exception e) {
            LOG.errorf(e, "LLM repair call failed for CV extraction");
        }
        return Optional.empty();
    }

    private String runChat(String systemPrompt, String userPrompt) {
        return chatModel.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        ).aiMessage().text();
    }

    private Optional<ProfileSchema> parseProfileSchema(String content) {
        try {
            String raw = StringUtils.stripMarkdownFences(content);
            String jsonCandidate = extractJsonCandidate(raw);
            JsonNode root = objectMapper.readTree(jsonCandidate);
            if (!(root instanceof ObjectNode objectRoot)) {
                return Optional.empty();
            }
            normalizeSchemaNode(objectRoot);
            ProfileSchema extracted = objectMapper.treeToValue(objectRoot, ProfileSchema.class);
            return Optional.ofNullable(sanitizeSchema(extracted));
        } catch (Exception e) {
            LOG.debugf(e, "Failed to parse profile schema candidate");
            return Optional.empty();
        }
    }

    private static String extractJsonCandidate(String raw) {
        if (raw == null) return "";
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return raw.substring(first, last + 1);
        }
        return raw;
    }

    private void normalizeSchemaNode(ObjectNode root) {
        ObjectNode professional = objectNodeOrCreate(root, "professional");
        normalizeStringArrayField(professional, "roles");
        normalizeStringArrayField(professional, "industries");
        normalizeStringArrayField(professional, "skills_technical");
        normalizeStringArrayField(professional, "skills_soft");
        normalizeStringArrayField(professional, "tools_and_tech");
        normalizeStringArrayField(professional, "languages_spoken");
        normalizeEnumField(professional, "seniority", List.of("JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"));
        normalizeEnumField(professional, "work_mode_preference", List.of("REMOTE", "HYBRID", "OFFICE", "FLEXIBLE"));
        normalizeEnumField(professional, "employment_type", List.of("EMPLOYED", "FREELANCE", "FOUNDER", "LOOKING", "OPEN_TO_OFFERS"));

        ObjectNode contacts = objectNodeOrCreate(root, "contacts");
        copyLegacyContactField(professional, contacts, "slack_handle");
        copyLegacyContactField(professional, contacts, "telegram_handle");
        copyLegacyContactField(professional, contacts, "mobile_phone");
        copyLegacyContactField(professional, contacts, "linkedin_url");
        professional.remove(List.of("slack_handle", "telegram_handle", "mobile_phone", "linkedin_url"));
        normalizeStringField(contacts, "slack_handle");
        normalizeStringField(contacts, "telegram_handle");
        normalizeStringField(contacts, "mobile_phone");
        normalizeStringField(contacts, "linkedin_url");

        ObjectNode interests = objectNodeOrCreate(root, "interests_professional");
        normalizeStringArrayField(interests, "topics_frequent");
        normalizeStringArrayField(interests, "learning_areas");
        normalizeStringArrayField(interests, "project_types");

        ObjectNode personal = objectNodeOrCreate(root, "personal");
        normalizeStringArrayField(personal, "hobbies");
        normalizeStringArrayField(personal, "sports");
        normalizeStringArrayField(personal, "education");
        normalizeStringArrayField(personal, "causes");
        normalizeStringArrayField(personal, "personality_tags");
        normalizeStringArrayField(personal, "music_genres");
        normalizeStringArrayField(personal, "book_genres");

        ObjectNode geography = objectNodeOrCreate(root, "geography");
        normalizeStringField(geography, "country");
        normalizeStringField(geography, "city");
        normalizeStringField(geography, "timezone");

        ObjectNode identity = objectNodeOrCreate(root, "identity");
        normalizeStringField(identity, "display_name");
        normalizeStringField(identity, "first_name");
        normalizeStringField(identity, "last_name");
        normalizeStringField(identity, "email");
        normalizeStringField(identity, "photo_url");
        normalizeStringField(identity, "company");
        normalizeStringField(identity, "birth_date");
    }

    private static void copyLegacyContactField(ObjectNode professional, ObjectNode contacts, String key) {
        if (!contacts.hasNonNull(key) && professional.hasNonNull(key)) {
            contacts.set(key, professional.get(key));
        }
    }

    private static ObjectNode objectNodeOrCreate(ObjectNode root, String field) {
        JsonNode node = root.get(field);
        if (node instanceof ObjectNode out) {
            return out;
        }
        ObjectNode created = root.objectNode();
        root.set(field, created);
        return created;
    }

    private static void normalizeStringField(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String v = node.asText().trim();
            if (v.isBlank()) parent.putNull(field);
            else parent.put(field, v);
            return;
        }
        if (node.isArray() && node.size() > 0 && node.get(0).isTextual()) {
            String v = node.get(0).asText().trim();
            if (v.isBlank()) parent.putNull(field);
            else parent.put(field, v);
            return;
        }
        parent.putNull(field);
    }

    private static void normalizeEnumField(ObjectNode parent, String field, List<String> allowed) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return;
        String value = node.asText(null);
        if (value == null) {
            parent.putNull(field);
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            parent.putNull(field);
            return;
        }
        parent.put(field, normalized);
    }

    private static void normalizeStringArrayField(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return;
        ArrayNode out = parent.arrayNode();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = asNormalizedString(item);
                if (value != null) out.add(value);
            }
        } else if (node.isObject()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String value = asNormalizedString(values.next());
                if (value != null) out.add(value);
            }
        } else {
            String value = asNormalizedString(node);
            if (value != null) out.add(value);
        }
        if (out.isEmpty()) {
            parent.putNull(field);
        } else {
            parent.set(field, dedupeArray(out));
        }
    }

    private static ArrayNode dedupeArray(ArrayNode input) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayNode out = input.arrayNode();
        for (JsonNode item : input) {
            if (!item.isTextual()) continue;
            String v = item.asText().trim();
            if (v.isBlank()) continue;
            if (seen.add(v.toLowerCase(Locale.ROOT))) {
                out.add(v);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    private static String asNormalizedString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) {
            String v = node.asText().trim();
            return v.isBlank() ? null : v;
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private ProfileSchema sanitizeSchema(ProfileSchema schema) {
        if (schema == null || schema.professional() == null) {
            return schema;
        }
        var p = schema.professional();
        var sanitizedTools = sanitizeToolsAndTech(p.toolsAndTech());
        var sanitizedProfessional = new ProfileSchema.ProfessionalInfo(
                p.roles(),
                p.seniority(),
                p.industries(),
                p.skillsTechnical(),
                p.skillsSoft(),
                sanitizedTools,
                p.languagesSpoken(),
                p.workModePreference(),
                p.employmentType()
        );
        return new ProfileSchema(
                schema.profileVersion(),
                schema.generatedAt(),
                schema.consent(),
                sanitizedProfessional,
                schema.contacts(),
                schema.interestsProfessional(),
                schema.personal(),
                schema.geography(),
                schema.fieldProvenance(),
                schema.identity()
        );
    }

    private java.util.List<String> sanitizeToolsAndTech(java.util.List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return tools;
        }
        var deduped = new LinkedHashSet<String>();
        for (String tool : tools) {
            if (tool == null || tool.isBlank()) {
                continue;
            }
            if (isNonToolEntity(tool)) {
                continue;
            }
            deduped.add(tool.trim());
        }
        if (deduped.isEmpty()) {
            return null;
        }
        return java.util.List.copyOf(deduped);
    }

    private boolean isNonToolEntity(String value) {
        String lower = value.toLowerCase(Locale.ROOT).trim();
        return lower.contains("summit")
                || lower.contains("conference")
                || lower.contains("event")
                || lower.contains("webinar")
                || lower.contains("publication")
                || lower.contains("article")
                || lower.contains("certified")
                || lower.contains("certification");
    }

    private void logExtractionSummary(ProfileSchema schema) {
        if (schema == null) {
            LOG.warn("Profile structuring LLM extraction produced null schema");
            return;
        }
        var p = schema.professional();
        int roles = p != null && p.roles() != null ? p.roles().size() : 0;
        int industries = p != null && p.industries() != null ? p.industries().size() : 0;
        int skillsTech = p != null && p.skillsTechnical() != null ? p.skillsTechnical().size() : 0;
        int skillsSoft = p != null && p.skillsSoft() != null ? p.skillsSoft().size() : 0;
        int tools = p != null && p.toolsAndTech() != null ? p.toolsAndTech().size() : 0;
        int langs = p != null && p.languagesSpoken() != null ? p.languagesSpoken().size() : 0;
        String seniority = p != null && p.seniority() != null ? p.seniority().name() : "null";

        LOG.debugf(
                "Profile structuring LLM extraction summary: roles=%d industries=%d skillsTech=%d skillsSoft=%d tools=%d langs=%d seniority=%s",
                roles, industries, skillsTech, skillsSoft, tools, langs, seniority
        );
    }

}
