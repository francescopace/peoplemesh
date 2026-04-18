package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.ParsedSearchQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class HeuristicSearchQueryParser implements SearchQueryParser {

    private static final Pattern TOKEN_TRIM_PATTERN = Pattern.compile("^[^\\p{Alnum}]+|[^\\p{Alnum}]+$");
    private static final Pattern ALL_SCOPE_PATTERN = Pattern.compile(
            "\\b(all|tutti|everything)\\b|\\bany\\s+type\\b|\\btutti\\s+i\\s+risultati\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "shall", "must",
            "need", "not", "no", "nor", "so", "if", "then", "than", "that",
            "this", "these", "those", "it", "its", "i", "we", "you", "he", "she",
            "they", "me", "him", "her", "us", "them", "my", "your", "his", "our",
            "their", "who", "whom", "which", "what", "where", "when", "how", "why",
            "each", "every", "both", "few", "more", "most", "some", "any",
            "such", "only", "very", "just", "also", "about", "up", "out", "into",
            "over", "after", "before", "between", "under", "above", "below",
            "looking", "find", "search", "want", "needed", "required",
            "experience", "experienced", "expertise", "expert", "proficient",
            "knowledge", "familiar", "background", "developer", "engineer",
            "consultant", "specialist", "professional", "person", "someone",
            "anybody", "people", "team", "work", "working", "role"
    );

    private static final Set<String> CONTEXT_KEYWORDS = Set.of(
            "community", "communities", "event", "events", "job", "jobs",
            "opportunity", "opportunities", "networking", "meetup", "meetups",
            "conference", "conferences", "project", "projects", "group", "groups"
    );

    private static final Map<String, String> ROLE_WORD_ALIASES = Map.ofEntries(
            Map.entry("developer", "developer"),
            Map.entry("dev", "developer"),
            Map.entry("engineer", "engineer"),
            Map.entry("architect", "architect"),
            Map.entry("analyst", "analyst"),
            Map.entry("designer", "designer"),
            Map.entry("manager", "manager"),
            Map.entry("consultant", "consultant"),
            Map.entry("specialist", "specialist"),
            Map.entry("lead", "lead")
    );

    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("english", "English"),
            Map.entry("italian", "Italian"),
            Map.entry("italiano", "Italian"),
            Map.entry("french", "French"),
            Map.entry("francese", "French"),
            Map.entry("spanish", "Spanish"),
            Map.entry("spagnolo", "Spanish"),
            Map.entry("german", "German"),
            Map.entry("tedesco", "German"),
            Map.entry("portuguese", "Portuguese"),
            Map.entry("portoghese", "Portuguese"),
            Map.entry("dutch", "Dutch"),
            Map.entry("polish", "Polish"),
            Map.entry("swedish", "Swedish"),
            Map.entry("norwegian", "Norwegian"),
            Map.entry("danish", "Danish"),
            Map.entry("finnish", "Finnish"),
            Map.entry("romanian", "Romanian"),
            Map.entry("russian", "Russian"),
            Map.entry("ukrainian", "Ukrainian"),
            Map.entry("arabic", "Arabic"),
            Map.entry("hindi", "Hindi"),
            Map.entry("chinese", "Chinese"),
            Map.entry("mandarin", "Chinese"),
            Map.entry("japanese", "Japanese"),
            Map.entry("korean", "Korean"),
            Map.entry("turkish", "Turkish")
    );

    @Override
    public Optional<ParsedSearchQuery> parse(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return Optional.empty();
        }

        List<String> skills = new ArrayList<>();
        Set<String> roles = new LinkedHashSet<>();
        Set<String> keywords = new LinkedHashSet<>();
        Set<String> languages = new LinkedHashSet<>();

        for (String rawToken : userQuery.split("\\s+")) {
            String token = TOKEN_TRIM_PATTERN.matcher(rawToken).replaceAll("").trim();
            if (token.length() <= 1) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            String canonicalRole = ROLE_WORD_ALIASES.get(normalized);
            if (canonicalRole != null) {
                roles.add(canonicalRole);
                keywords.add(canonicalRole);
                continue;
            }
            if (STOP_WORDS.contains(normalized)) {
                continue;
            }
            String canonicalLanguage = LANGUAGE_ALIASES.get(normalized);
            if (canonicalLanguage != null) {
                languages.add(canonicalLanguage);
                continue;
            }
            if (CONTEXT_KEYWORDS.contains(normalized)) {
                keywords.add(token);
                continue;
            }
            skills.add(token);
            keywords.add(token);
        }

        String resultScope = inferResultScope(userQuery, skills, roles, keywords);

        return Optional.of(new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(
                        skills,
                        null,
                        new ArrayList<>(roles),
                        new ArrayList<>(languages),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                new ParsedSearchQuery.NiceToHaveFilters(
                        Collections.emptyList(),
                        null,
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "unknown",
                null,
                new ArrayList<>(keywords),
                userQuery,
                resultScope
        ));
    }

    private String inferResultScope(String userQuery, List<String> skills, Set<String> roles, Set<String> keywords) {
        if (userQuery != null && ALL_SCOPE_PATTERN.matcher(userQuery).find()) {
            return "all";
        }

        Set<String> normalizedKeywords = keywords.stream()
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String loweredQuery = userQuery != null ? userQuery.toLowerCase(Locale.ROOT) : "";

        if (normalizedKeywords.contains("community") || normalizedKeywords.contains("communities")) {
            return "communities";
        }
        if (normalizedKeywords.contains("event") || normalizedKeywords.contains("events")
                || normalizedKeywords.contains("meetup") || normalizedKeywords.contains("meetups")
                || normalizedKeywords.contains("conference") || normalizedKeywords.contains("conferences")) {
            return "events";
        }
        if (normalizedKeywords.contains("job") || normalizedKeywords.contains("jobs")
                || normalizedKeywords.contains("opportunity") || normalizedKeywords.contains("opportunities")
                || loweredQuery.contains("open role") || loweredQuery.contains("open roles")) {
            return "jobs";
        }
        if (normalizedKeywords.contains("project") || normalizedKeywords.contains("projects")) {
            return "projects";
        }
        if (normalizedKeywords.contains("group") || normalizedKeywords.contains("groups")) {
            return "groups";
        }
        if (!skills.isEmpty() || !roles.isEmpty()) {
            return "people";
        }
        return "unknown";
    }
}
