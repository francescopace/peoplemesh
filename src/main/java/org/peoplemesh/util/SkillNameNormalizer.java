package org.peoplemesh.util;

import java.util.Locale;

public final class SkillNameNormalizer {

    private SkillNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.replaceAll("\\s+", " ");
    }
}
