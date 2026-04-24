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
import org.peoplemesh.util.ProfileSchemaNormalization;
import org.peoplemesh.util.ProfileSchemaSanitizer;
import org.peoplemesh.util.StringUtils;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class CvLlmProfileStructuringService {

    private static final Logger LOG = Logger.getLogger(CvLlmProfileStructuringService.class);

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
            - project_types must contain only concrete project/domain categories explicitly evidenced by the CV (e.g. platform engineering, developer portals, pre-sales enablement); if none are explicit, set project_types to null.
            - Classify each signal in exactly one best-fit field: skills_technical vs tools_and_tech vs learning_areas vs project_types.
            - Do not use generic buckets; keep entries atomic and canonical (e.g. "Kubernetes", "Platform Engineering").
            - Populate contacts.* fields when explicitly present in CV: mobile_phone, linkedin_url, telegram_handle, slack_handle.
            - Populate identity fields when explicitly present in CV: display_name, first_name, last_name, email, photo_url, company.
            - Populate personal section when explicitly present (hobbies, sports, education, causes, personality_tags, music_genres, book_genres).
            - birth_date must be YYYY-MM-DD when explicitly present, otherwise null.
            - Respect ProfileSchema limits for list sizes and string lengths.
            - personal.* fields MUST be arrays of strings or null (never objects).
            - Keep arrays concise, deduplicated, and free of near-duplicates.
            - Return EXACTLY and ONLY the keys shown in the schema. Do not add extra top-level or nested keys.
            - Do NOT include timeline/history/achievement/certification sections unless they map to existing schema fields.
            - Keep output compact with these hard caps:
              - professional.industries: max 4
              - professional.skills_technical: max 12
              - professional.skills_soft: max 8
              - professional.tools_and_tech: max 12
              - professional.languages_spoken: max 6
              - interests_professional.learning_areas: max 4
              - interests_professional.project_types: max 4
              - personal.* arrays: max 4 items each
            - If CV contains many candidates, keep only the most representative/high-signal items.
            - Prefer null instead of long low-confidence lists.
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
    public ProfileSchema extractProfile(String cvContent) {
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
            throw new IllegalStateException("LLM call failed for CV extraction", e);
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned an empty payload for CV extraction");
        }

        ProfileSchema parsed = parseProfileSchema(content);
        logExtractionSummary(parsed);
        return parsed;
    }

    private String runChat(String systemPrompt, String userPrompt) {
        return chatModel.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        ).aiMessage().text();
    }

    private ProfileSchema parseProfileSchema(String content) {
        try {
            String raw = StringUtils.stripMarkdownFences(content);
            String jsonCandidate = extractJsonCandidate(raw);
            JsonNode root = objectMapper.readTree(jsonCandidate);
            if (!(root instanceof ObjectNode objectRoot)) {
                throw new IllegalStateException("LLM response root is not a JSON object");
            }
            normalizeSchemaNode(objectRoot);
            ProfileSchema extracted = objectMapper.treeToValue(objectRoot, ProfileSchema.class);
            ProfileSchema sanitized = ProfileSchemaSanitizer.sanitizeStructuredSchema(extracted);
            if (sanitized == null) {
                throw new IllegalStateException("Sanitized profile schema is null");
            }
            return sanitized;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse profile schema candidate");
            throw new IllegalStateException("Failed to parse profile schema candidate", e);
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
        normalizeStringArrayField(professional, "roles", ProfileSchema.MAX_ROLES, ProfileSchema.MAX_ROLE_LENGTH);
        normalizeStringArrayField(professional, "industries", ProfileSchema.MAX_INDUSTRIES, ProfileSchema.MAX_INDUSTRY_LENGTH);
        normalizeStringArrayField(professional, "skills_technical", ProfileSchema.MAX_SKILLS_TECHNICAL, ProfileSchema.MAX_SKILL_TECHNICAL_LENGTH);
        normalizeStringArrayField(professional, "skills_soft", ProfileSchema.MAX_SKILLS_SOFT, ProfileSchema.MAX_SKILL_SOFT_LENGTH);
        normalizeStringArrayField(professional, "tools_and_tech", ProfileSchema.MAX_TOOLS_AND_TECH, ProfileSchema.MAX_TOOL_AND_TECH_LENGTH);
        normalizeStringArrayField(professional, "languages_spoken", ProfileSchema.MAX_LANGUAGES_SPOKEN, ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH);
        normalizeLanguageArrayField(professional, "languages_spoken", ProfileSchema.MAX_LANGUAGES_SPOKEN, ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH);
        normalizeEnumField(professional, "seniority", List.of("JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"));
        normalizeEnumField(professional, "work_mode_preference", List.of("REMOTE", "HYBRID", "OFFICE", "FLEXIBLE"));
        normalizeEnumField(professional, "employment_type", List.of("EMPLOYED", "FREELANCE", "FOUNDER", "LOOKING", "OPEN_TO_OFFERS"));

        ObjectNode contacts = objectNodeOrCreate(root, "contacts");
        copyLegacyContactField(professional, contacts, "slack_handle");
        copyLegacyContactField(professional, contacts, "telegram_handle");
        copyLegacyContactField(professional, contacts, "mobile_phone");
        copyLegacyContactField(professional, contacts, "linkedin_url");
        professional.remove(List.of("slack_handle", "telegram_handle", "mobile_phone", "linkedin_url"));
        normalizeStringField(contacts, "slack_handle", ProfileSchema.MAX_SLACK_HANDLE_LENGTH);
        normalizeStringField(contacts, "telegram_handle", ProfileSchema.MAX_TELEGRAM_HANDLE_LENGTH);
        normalizeStringField(contacts, "mobile_phone", ProfileSchema.MAX_MOBILE_PHONE_LENGTH);
        normalizeStringField(contacts, "linkedin_url", ProfileSchema.MAX_LINKEDIN_URL_LENGTH);

        ObjectNode interests = objectNodeOrCreate(root, "interests_professional");
        normalizeStringArrayField(interests, "learning_areas", ProfileSchema.MAX_LEARNING_AREAS, ProfileSchema.MAX_LEARNING_AREA_LENGTH);
        normalizeStringArrayField(interests, "project_types", ProfileSchema.MAX_PROJECT_TYPES, ProfileSchema.MAX_PROJECT_TYPE_LENGTH);

        ObjectNode personal = objectNodeOrCreate(root, "personal");
        normalizeStringArrayField(personal, "hobbies", ProfileSchema.MAX_HOBBIES, ProfileSchema.MAX_HOBBY_LENGTH);
        normalizeStringArrayField(personal, "sports", ProfileSchema.MAX_SPORTS, ProfileSchema.MAX_SPORT_LENGTH);
        normalizeStringArrayField(personal, "education", ProfileSchema.MAX_EDUCATION, ProfileSchema.MAX_EDUCATION_LENGTH);
        normalizeStringArrayField(personal, "causes", ProfileSchema.MAX_CAUSES, ProfileSchema.MAX_CAUSE_LENGTH);
        normalizeStringArrayField(personal, "personality_tags", ProfileSchema.MAX_PERSONALITY_TAGS, ProfileSchema.MAX_PERSONALITY_TAG_LENGTH);
        normalizeStringArrayField(personal, "music_genres", ProfileSchema.MAX_MUSIC_GENRES, ProfileSchema.MAX_MUSIC_GENRE_LENGTH);
        normalizeStringArrayField(personal, "book_genres", ProfileSchema.MAX_BOOK_GENRES, ProfileSchema.MAX_BOOK_GENRE_LENGTH);

        ObjectNode geography = objectNodeOrCreate(root, "geography");
        normalizeStringField(geography, "country", ProfileSchema.MAX_COUNTRY_LENGTH);
        normalizeStringField(geography, "city", ProfileSchema.MAX_CITY_LENGTH);
        normalizeStringField(geography, "timezone", ProfileSchema.MAX_TIMEZONE_LENGTH);

        ObjectNode identity = objectNodeOrCreate(root, "identity");
        normalizeStringField(identity, "display_name", ProfileSchema.MAX_IDENTITY_DISPLAY_NAME_LENGTH);
        normalizeStringField(identity, "first_name", ProfileSchema.MAX_IDENTITY_FIRST_NAME_LENGTH);
        normalizeStringField(identity, "last_name", ProfileSchema.MAX_IDENTITY_LAST_NAME_LENGTH);
        normalizeStringField(identity, "email", ProfileSchema.MAX_IDENTITY_EMAIL_LENGTH);
        normalizeStringField(identity, "photo_url", ProfileSchema.MAX_IDENTITY_PHOTO_URL_LENGTH);
        normalizeStringField(identity, "company", ProfileSchema.MAX_IDENTITY_COMPANY_LENGTH);
        normalizeStringField(identity, "birth_date", ProfileSchema.MAX_IDENTITY_BIRTH_DATE_LENGTH);
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

    private static void normalizeStringField(ObjectNode parent, String field, int maxLength) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String v = ProfileSchemaNormalization.normalizeString(node.asText(), maxLength);
            if (v == null) parent.putNull(field);
            else parent.put(field, v);
            return;
        }
        if (node.isArray() && node.size() > 0 && node.get(0).isTextual()) {
            String v = ProfileSchemaNormalization.normalizeString(node.get(0).asText(), maxLength);
            if (v == null) parent.putNull(field);
            else parent.put(field, v);
            return;
        }
        parent.putNull(field);
    }

    private static void normalizeLanguageArrayField(ObjectNode parent, String field, int maxItems, int maxLength) {
        JsonNode node = parent.get(field);
        if (!(node instanceof ArrayNode arrayNode)) {
            return;
        }
        List<String> raw = new java.util.ArrayList<>();
        arrayNode.forEach(child -> {
            if (child != null && child.isTextual()) {
                raw.add(child.asText());
            }
        });
        List<String> normalized = ProfileSchemaNormalization.normalizeLanguages(raw, maxItems, maxLength);
        if (normalized == null || normalized.isEmpty()) {
            parent.putNull(field);
            return;
        }
        ArrayNode out = parent.arrayNode();
        normalized.forEach(out::add);
        parent.set(field, out);
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

    private static void normalizeStringArrayField(ObjectNode parent, String field, int maxItems, int maxItemLength) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) return;
        List<String> rawValues = new java.util.ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = asNormalizedString(item);
                if (value != null) rawValues.add(value);
            }
        } else if (node.isObject()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                String value = asNormalizedString(values.next());
                if (value != null) rawValues.add(value);
            }
        } else {
            String value = asNormalizedString(node);
            if (value != null) rawValues.add(value);
        }
        List<String> normalized = ProfileSchemaNormalization.normalizeList(rawValues, maxItems, maxItemLength);
        if (normalized == null || normalized.isEmpty()) {
            parent.putNull(field);
        } else {
            ArrayNode out = parent.arrayNode();
            normalized.forEach(out::add);
            parent.set(field, out);
        }
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
