package org.peoplemesh.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProfileSchemaNormalization {

    private static final Map<String, String> LANGUAGE_ALIASES = buildLanguageAliases();

    private ProfileSchemaNormalization() {}

    public static String normalizeString(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public static List<String> normalizeList(List<String> values, int maxItems, int maxItemLength) {
        if (values == null || values.isEmpty()) return null;
        Set<String> deduped = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String item = normalizeString(value, maxItemLength);
            if (item == null) continue;
            String key = item.toLowerCase(Locale.ROOT);
            if (!deduped.add(key)) continue;
            normalized.add(item);
            if (normalized.size() >= maxItems) break;
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public static List<String> normalizeLanguages(List<String> values, int maxItems, int maxItemLength) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> deduped = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String item = normalizeLanguage(value, maxItemLength);
            if (item == null) {
                continue;
            }
            String key = item.toLowerCase(Locale.ROOT);
            if (!deduped.add(key)) {
                continue;
            }
            normalized.add(item);
            if (normalized.size() >= maxItems) {
                break;
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeLanguage(String value, int maxLength) {
        String trimmed = normalizeString(value, maxLength);
        if (trimmed == null) {
            return null;
        }
        String withoutQualifier = trimmed
                .replaceAll("\\s*\\([^)]*\\)\\s*", " ")
                .replace('_', '-')
                .trim()
                .replaceAll("\\s+", " ");
        if (withoutQualifier.isBlank()) {
            return null;
        }
        String normalizedKey = withoutQualifier.toLowerCase(Locale.ROOT);
        String localeCode = normalizedKey;
        int separatorIdx = localeCode.indexOf('-');
        if (separatorIdx > 0) {
            localeCode = localeCode.substring(0, separatorIdx);
        }
        if (localeCode.length() >= 2 && localeCode.length() <= 8 && localeCode.chars().allMatch(Character::isLetter)) {
            String display = Locale.of(localeCode).getDisplayLanguage(Locale.ENGLISH);
            if (display != null && !display.isBlank() && !display.equalsIgnoreCase(localeCode)) {
                return normalizeString(display, maxLength);
            }
        }
        String canonical = LANGUAGE_ALIASES.get(normalizedKey);
        if (canonical != null) {
            return normalizeString(canonical, maxLength);
        }
        return normalizeString(toTitleCase(withoutQualifier), maxLength);
    }

    private static String toTitleCase(String value) {
        String[] parts = value.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (part.length() == 1) {
                out.add(part.toUpperCase(Locale.ROOT));
                continue;
            }
            out.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", out);
    }

    private static Map<String, String> buildLanguageAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("english", "English");
        aliases.put("italian", "Italian");
        aliases.put("french", "French");
        aliases.put("spanish", "Spanish");
        aliases.put("portuguese", "Portuguese");
        aliases.put("german", "German");
        aliases.put("dutch", "Dutch");
        aliases.put("hindi", "Hindi");
        aliases.put("japanese", "Japanese");
        aliases.put("chinese", "Chinese");
        aliases.put("mandarin", "Chinese");
        aliases.put("arabic", "Arabic");
        aliases.put("russian", "Russian");
        aliases.put("ukrainian", "Ukrainian");
        aliases.put("polish", "Polish");
        aliases.put("turkish", "Turkish");
        aliases.put("korean", "Korean");
        aliases.put("greek", "Greek");
        aliases.put("romanian", "Romanian");
        aliases.put("swedish", "Swedish");
        aliases.put("norwegian", "Norwegian");
        aliases.put("danish", "Danish");
        aliases.put("finnish", "Finnish");
        aliases.put("czech", "Czech");
        aliases.put("hungarian", "Hungarian");
        return Map.copyOf(aliases);
    }
}
