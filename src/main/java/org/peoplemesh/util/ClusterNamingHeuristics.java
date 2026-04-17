package org.peoplemesh.util;

import org.peoplemesh.service.ClusterNamingLlm;

import java.util.*;

/**
 * Shared heuristic naming logic for cluster communities,
 * used as fallback by {@link org.peoplemesh.service.LlmClusterNaming} when LLM parsing fails.
 */
public final class ClusterNamingHeuristics {

    private static final List<String> TRAIT_KEYS = List.of("skills", "hobbies", "sports", "causes", "topics");

    private ClusterNamingHeuristics() {}

    public static Optional<ClusterNamingLlm.ClusterName> fromTraits(Map<String, List<String>> traits) {
        List<String> keywords = new ArrayList<>();
        for (String key : TRAIT_KEYS) {
            List<String> vals = traits.getOrDefault(key, Collections.emptyList());
            if (!vals.isEmpty()) {
                keywords.addAll(vals.stream().limit(3).toList());
            }
        }
        if (keywords.isEmpty()) return Optional.empty();

        Set<String> unique = new LinkedHashSet<>(keywords);
        List<String> topKeywords = unique.stream().limit(3).toList();
        String joined = String.join(", ", topKeywords.stream().map(ClusterNamingHeuristics::titleCase).toList());
        String title = joined + " Community";
        String description = "An auto-discovered community of people interested in " + joined.toLowerCase() + ".";
        List<String> tags = unique.stream().limit(5).toList();
        return Optional.of(new ClusterNamingLlm.ClusterName(title, description, tags));
    }

    static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
