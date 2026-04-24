package org.peoplemesh.util;

public final class SanitizeUtils {

    private SanitizeUtils() {}

    /**
     * Truncates text to {@code maxLength} and encodes HTML-significant characters
     * as entities (preserving apostrophes and other legitimate punctuation).
     */
    public static String sanitize(String text, int maxLength) {
        if (text == null) return null;
        String truncated = text.length() > maxLength
                ? text.substring(0, maxLength)
                : text;
        return truncated
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

}
