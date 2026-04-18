package org.peoplemesh.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared stateless helpers for search and matching pipelines.
 */
public final class SearchMatchingUtils {

    private SearchMatchingUtils() {}

    public static List<String> deduplicateTerms(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        List<String> deduplicated = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String key = normalizeTerm(value);
            if (seen.add(key)) {
                deduplicated.add(value.trim());
            }
        }
        return deduplicated;
    }

    public static <T> List<T> paginate(List<T> all, Integer requestedLimit, Integer requestedOffset, int defaultLimit) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        int safeOffset = requestedOffset != null ? Math.max(0, requestedOffset) : 0;
        int safeLimit = requestedLimit != null ? Math.max(1, requestedLimit) : defaultLimit;
        if (safeOffset >= all.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(all.size(), safeOffset + safeLimit);
        return all.subList(safeOffset, toIndex);
    }

    private static String normalizeTerm(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        normalized = normalized
                .replace("c++", "cpp")
                .replace("c#", "csharp")
                .replace("f#", "fsharp")
                .replace(".net", "dotnet")
                .replace("node.js", "nodejs");
        normalized = normalized.replaceAll("[^\\p{Alnum}]+", " ").trim();
        return normalized.replaceAll("\\s+", " ");
    }
}
