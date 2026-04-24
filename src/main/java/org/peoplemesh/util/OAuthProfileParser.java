package org.peoplemesh.util;

import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.Seniority;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link ProfileSchema} from any OAuth provider's OIDC data.
 */
public final class OAuthProfileParser {

    private OAuthProfileParser() {}

    public static ProfileSchema buildImportSchema(String provider, OidcSubject subject) {
        List<String> roles = null;
        Seniority seniority = null;
        String country = countryFromLocale(subject.locale());
        List<String> languages = ProfileSchemaNormalization.normalizeLanguages(
                singletonOrEmpty(languageFromLocale(subject.locale())),
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        );
        List<String> industries = ProfileSchemaNormalization.normalizeList(
                singletonOrEmpty(subject.industry()),
                ProfileSchema.MAX_INDUSTRIES,
                ProfileSchema.MAX_INDUSTRY_LENGTH
        );
        List<String> skills = null;
        ProfileSchema.IdentityInfo identity = toIdentity(subject);

        return new ProfileSchema(
                "1.0",
                Instant.now().atOffset(ZoneOffset.UTC).toInstant(),
                null,
                new ProfileSchema.ProfessionalInfo(
                        roles, seniority, industries, skills,
                        null, null, languages, null, null
                ),
                null,
                null,
                null,
                new ProfileSchema.GeographyInfo(country, null, null),
                providerIdentityProvenance(provider, roles != null, languages != null, skills != null, identity),
                identity
        );
    }

    public static ProfileSchema buildEnrichedGitHubSchema(GitHubEnrichedResult enriched) {
        OidcSubject subject = enriched.subject();
        Seniority seniority = null;
        String country = countryFromLocale(subject.locale());
        List<String> rolesNormalized = null;
        List<String> languages = ProfileSchemaNormalization.normalizeLanguages(
                singletonOrEmpty(languageFromLocale(subject.locale())),
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        );

        List<String> skills = ProfileSchemaNormalization.normalizeList(enriched.languages(),
                ProfileSchema.MAX_SKILLS_TECHNICAL, ProfileSchema.MAX_SKILL_TECHNICAL_LENGTH);
        ProfileSchema.IdentityInfo identity = toIdentity(subject);

        return new ProfileSchema(
                "1.0",
                Instant.now().atOffset(ZoneOffset.UTC).toInstant(),
                null,
                new ProfileSchema.ProfessionalInfo(
                        rolesNormalized, seniority, null, skills,
                        null, null, languages, null, null
                ),
                null,
                null,
                null,
                new ProfileSchema.GeographyInfo(country, null, null),
                providerIdentityProvenance("github", rolesNormalized != null, languages != null, skills != null, identity),
                identity
        );
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
        if (lang.length() < 2 || lang.length() > 8) {
            return null;
        }
        return ProfileSchemaNormalization.normalizeLanguage(lang, ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH);
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
            ProfileSchema.IdentityInfo identity
    ) {
        Map<String, String> provenance = new LinkedHashMap<>();
        if (hasInferredRole) provenance.put("professional.roles", provider);
        if (hasSkills) provenance.put("professional.skills_technical", provider);
        if (hasLanguage) provenance.put("professional.languages_spoken", provider);
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
        String displayName = ProfileSchemaNormalization.normalizeString(
                fullNameOrNull(subject),
                ProfileSchema.MAX_IDENTITY_DISPLAY_NAME_LENGTH
        );
        String firstName = ProfileSchemaNormalization.normalizeString(
                subject.givenName(),
                ProfileSchema.MAX_IDENTITY_FIRST_NAME_LENGTH
        );
        String lastName = ProfileSchemaNormalization.normalizeString(
                subject.familyName(),
                ProfileSchema.MAX_IDENTITY_LAST_NAME_LENGTH
        );
        String email = ProfileSchemaNormalization.normalizeString(
                subject.email(),
                ProfileSchema.MAX_IDENTITY_EMAIL_LENGTH
        );
        String photoUrl = ProfileSchemaNormalization.normalizeString(
                subject.picture(),
                ProfileSchema.MAX_IDENTITY_PHOTO_URL_LENGTH
        );
        String company = ProfileSchemaNormalization.normalizeString(
                subject.hostedDomain(),
                ProfileSchema.MAX_IDENTITY_COMPANY_LENGTH
        );
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

    private static List<String> singletonOrEmpty(String value) {
        return value == null ? List.of() : List.of(value);
    }

}
