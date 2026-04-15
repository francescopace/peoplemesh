package org.peoplemesh.util;

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
}
