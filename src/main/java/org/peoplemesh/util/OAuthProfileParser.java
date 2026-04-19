package org.peoplemesh.util;

import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.Seniority;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link ProfileSchema} from any OAuth provider's OIDC data.
 * Headline/bio parsing (roles, seniority, skills) applies when the provider
 * supplies that field (e.g. GitHub bio).
 */
public final class OAuthProfileParser {

    private OAuthProfileParser() {}

    public static ProfileSchema buildImportSchema(String provider, OidcSubject subject) {
        String headline = subject.headline();
        List<String> roles = optionalList(normalizeRoleFromHeadline(headline));
        Seniority seniority = inferSeniority(headline);
        String country = countryFromLocale(subject.locale());
        String language = languageFromLocale(subject.locale());
        List<String> industries = optionalList(subject.industry());
        List<String> topics = optionalList(headline);
        List<String> skills = optionalList(extractKnownSkills(headline));
        ProfileSchema.IdentityInfo identity = toIdentity(subject);

        return new ProfileSchema(
                "1.0",
                Instant.now().atOffset(ZoneOffset.UTC).toInstant(),
                null,
                new ProfileSchema.ProfessionalInfo(
                        roles, seniority, industries, skills,
                        null, null, optionalList(language), null, null
                ),
                null,
                new ProfileSchema.InterestsInfo(topics, null, null),
                null,
                new ProfileSchema.GeographyInfo(country, null, null),
                providerIdentityProvenance(provider, roles != null, language != null, skills != null, topics != null, identity),
                identity
        );
    }

    public static ProfileSchema buildEnrichedGitHubSchema(GitHubEnrichedResult enriched) {
        OidcSubject subject = enriched.subject();
        String headline = subject.headline();
        List<String> roles = optionalList(normalizeRoleFromHeadline(headline));
        Seniority seniority = inferSeniority(headline);
        String country = countryFromLocale(subject.locale());
        String language = languageFromLocale(subject.locale());

        Set<String> skillSet = new LinkedHashSet<>();
        skillSet.addAll(extractKnownSkills(headline));
        if (enriched.languages() != null) skillSet.addAll(enriched.languages());
        List<String> skills = skillSet.isEmpty() ? null : new ArrayList<>(skillSet);
        ProfileSchema.IdentityInfo identity = toIdentity(subject);

        List<String> topics = enriched.topics() != null && !enriched.topics().isEmpty()
                ? enriched.topics() : optionalList(headline);

        return new ProfileSchema(
                "1.0",
                Instant.now().atOffset(ZoneOffset.UTC).toInstant(),
                null,
                new ProfileSchema.ProfessionalInfo(
                        roles, seniority, null, skills,
                        null, null, optionalList(language), null, null
                ),
                null,
                new ProfileSchema.InterestsInfo(topics, null, null),
                null,
                new ProfileSchema.GeographyInfo(country, null, null),
                providerIdentityProvenance("github", roles != null, language != null, skills != null, topics != null, identity),
                identity
        );
    }

    static Seniority inferSeniority(String headline) {
        if (headline == null || headline.isBlank()) return null;
        String h = headline.toLowerCase();
        if (h.contains("founder") || h.contains("cto") || h.contains("ceo") || h.contains("vp")
                || h.contains("head of") || h.contains("chief"))
            return Seniority.EXECUTIVE;
        if (h.contains("lead") || h.contains("principal") || h.contains("staff"))
            return Seniority.LEAD;
        if (h.contains("senior") || h.contains("sr "))
            return Seniority.SENIOR;
        if (h.contains("junior") || h.contains("intern") || h.contains("entry"))
            return Seniority.JUNIOR;
        return Seniority.MID;
    }

    static String normalizeRoleFromHeadline(String headline) {
        if (headline == null || headline.isBlank()) return null;
        String h = headline.trim();
        h = splitBefore(h, " at ");
        h = splitBefore(h, " @ ");
        h = splitBefore(h, " | ");
        h = splitBefore(h, " - ");
        h = splitBefore(h, " \u2022 ");
        h = h.replaceAll("\\s+", " ").trim();
        return h.isBlank() ? null : h;
    }

    static List<String> extractKnownSkills(String headline) {
        if (headline == null || headline.isBlank()) return List.of();
        String h = headline.toLowerCase();
        Set<String> skills = new LinkedHashSet<>();

        addIfContains(h, skills, "java", "Java");
        addIfContains(h, skills, "spring", "Spring");
        addIfContains(h, skills, "kotlin", "Kotlin");
        addIfContains(h, skills, "python", "Python");
        addIfContains(h, skills, "django", "Django");
        addIfContains(h, skills, "flask", "Flask");
        addIfContains(h, skills, "node.js", "Node.js");
        addIfContains(h, skills, "nodejs", "Node.js");
        addIfContains(h, skills, "typescript", "TypeScript");
        addIfContains(h, skills, "javascript", "JavaScript");
        addIfContains(h, skills, "react", "React");
        addIfContains(h, skills, "angular", "Angular");
        addIfContains(h, skills, "vue", "Vue");
        addIfContains(h, skills, "golang", "Go");
        addIfContains(h, skills, " go ", "Go");
        addIfContains(h, skills, "rust", "Rust");
        addIfContains(h, skills, ".net", ".NET");
        addIfContains(h, skills, "c#", "C#");
        addIfContains(h, skills, "c++", "C++");
        addIfContains(h, skills, "postgres", "PostgreSQL");
        addIfContains(h, skills, "mysql", "MySQL");
        addIfContains(h, skills, "mongodb", "MongoDB");
        addIfContains(h, skills, "redis", "Redis");
        addIfContains(h, skills, "kafka", "Kafka");
        addIfContains(h, skills, "docker", "Docker");
        addIfContains(h, skills, "kubernetes", "Kubernetes");
        addIfContains(h, skills, "aws", "AWS");
        addIfContains(h, skills, "azure", "Azure");
        addIfContains(h, skills, "gcp", "GCP");
        addIfContains(h, skills, "terraform", "Terraform");

        return List.copyOf(skills);
    }

    static String countryFromLocale(String locale) {
        if (locale == null || locale.isBlank()) return null;
        String normalized = locale.replace('_', '-');
        int idx = normalized.lastIndexOf('-');
        if (idx < 0 || idx + 1 >= normalized.length()) return null;
        String cc = normalized.substring(idx + 1).trim();
        return cc.length() == 2 ? cc.toUpperCase() : null;
    }

    static String languageFromLocale(String locale) {
        if (locale == null || locale.isBlank()) return null;
        String normalized = locale.replace('_', '-');
        int idx = normalized.indexOf('-');
        String lang = idx >= 0 ? normalized.substring(0, idx) : normalized;
        lang = lang.trim();
        return (lang.length() >= 2 && lang.length() <= 8) ? lang : null;
    }

    public static String fullNameOrNull(OidcSubject subject) {
        String direct = StringUtils.firstNonBlank(subject.fullName());
        if (direct != null) return direct;
        String given = StringUtils.firstNonBlank(subject.givenName());
        String family = StringUtils.firstNonBlank(subject.familyName());
        if (given != null && family != null) return given + " " + family;
        return StringUtils.firstNonBlank(given, family);
    }

    private static Map<String, String> providerIdentityProvenance(
            String provider,
            boolean hasInferredRole,
            boolean hasLanguage,
            boolean hasSkills,
            boolean hasTopics,
            ProfileSchema.IdentityInfo identity
    ) {
        Map<String, String> provenance = new LinkedHashMap<>();
        if (hasInferredRole) provenance.put("professional.roles", provider);
        if (hasSkills) provenance.put("professional.skills_technical", provider);
        if (hasLanguage) provenance.put("professional.languages_spoken", provider);
        if (hasTopics) provenance.put("interests_professional.topics_frequent", provider);
        provenance.put("geography.country", provider);
        if (identity != null) {
            if (identity.displayName() != null) provenance.put("identity.display_name", provider);
            if (identity.firstName() != null) provenance.put("identity.first_name", provider);
            if (identity.lastName() != null) provenance.put("identity.last_name", provider);
            if (identity.email() != null) provenance.put("identity.email", provider);
            if (identity.photoUrl() != null) provenance.put("identity.photo_url", provider);
            if (identity.company() != null) provenance.put("identity.company", provider);
        }
        return provenance;
    }

    private static ProfileSchema.IdentityInfo toIdentity(OidcSubject subject) {
        if (subject == null) return null;
        String displayName = fullNameOrNull(subject);
        String firstName = trimToNull(subject.givenName());
        String lastName = trimToNull(subject.familyName());
        String email = trimToNull(subject.email());
        String photoUrl = trimToNull(subject.picture());
        String company = trimToNull(subject.hostedDomain());
        if (displayName == null && firstName == null && lastName == null
                && email == null && photoUrl == null && company == null) {
            return null;
        }
        return new ProfileSchema.IdentityInfo(
                displayName,
                firstName,
                lastName,
                email,
                photoUrl,
                company,
                null
        );
    }

    private static List<String> optionalList(String value) {
        String v = value == null ? null : value.trim();
        return (v == null || v.isBlank()) ? null : List.of(v);
    }

    private static List<String> optionalList(List<String> values) {
        return (values == null || values.isEmpty()) ? null : values;
    }

    private static String splitBefore(String value, String marker) {
        if (marker == null || marker.isBlank()) return value;
        int idx = value.toLowerCase().indexOf(marker.toLowerCase());
        return idx <= 0 ? value : value.substring(0, idx).trim();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String out = value.trim();
        return out.isBlank() ? null : out;
    }

    private static void addIfContains(String haystack, Set<String> out, String needle, String normalized) {
        if (haystack.contains(needle)) out.add(normalized);
    }
}
