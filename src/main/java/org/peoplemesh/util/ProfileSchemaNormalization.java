package org.peoplemesh.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProfileSchemaNormalization {

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
}
