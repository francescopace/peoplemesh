package org.peoplemesh.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared string utilities used across the codebase.
 */
public final class StringUtils {

    private StringUtils() {}

    public static String abbreviate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...[truncated]";
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static final String DEFAULT_SUBJECT_PREFIX = "[PeopleMesh]";

    public static String buildNotificationSubject(String configuredPrefix, String action) {
        String prefix = (configuredPrefix == null || configuredPrefix.isBlank())
                ? DEFAULT_SUBJECT_PREFIX : configuredPrefix;
        String safeAction = (action == null || action.isBlank())
                ? "event" : action.toLowerCase().replace('_', ' ');
        return prefix + " " + safeAction;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public static List<String> splitCommaSeparated(String plain) {
        if (plain == null || plain.isBlank()) return Collections.emptyList();
        return Arrays.stream(plain.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static final Pattern MARKDOWN_FENCE =
            Pattern.compile("^\\s*```(?:\\w+)?\\s*\\n?(.*?)\\n?\\s*```\\s*$", Pattern.DOTALL);

    public static String stripMarkdownFences(String text) {
        if (text == null) return null;
        var m = MARKDOWN_FENCE.matcher(text.strip());
        return m.matches() ? m.group(1).strip() : text.strip();
    }
}
