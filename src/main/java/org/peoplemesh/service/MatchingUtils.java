package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.domain.model.SkillAssessment;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared utilities for matching and search pipelines.
 * Extracted from MatchingService to avoid duplication.
 */
public final class MatchingUtils {

    private MatchingUtils() {}

    public static String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    static List<String> parseArray(Object obj) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof String[] arr) return List.of(arr);
        if (obj instanceof java.sql.Array sqlArray) {
            try {
                Object value = sqlArray.getArray();
                if (value instanceof String[] arr) {
                    return List.of(arr);
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("null")
    static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        throw new IllegalArgumentException("Unsupported timestamp type: " + value.getClass().getName());
    }

    static List<String> combineLists(List<String> a, List<String> b) {
        List<String> combined = new ArrayList<>();
        if (a != null) combined.addAll(a);
        if (b != null) combined.addAll(b);
        return combined;
    }

    static double jaccardSimilarity(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> setA = a.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> setB = b.stream().map(String::toLowerCase).collect(Collectors.toSet());

        long matched = setA.stream().filter(sa -> fuzzyContains(setB, sa)).count();
        int unionSize = setA.size() + setB.size() - (int) matched;
        return unionSize <= 0 ? 0.0 : (double) matched / unionSize;
    }

    static List<String> intersectCaseInsensitive(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> bLower = b.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> intersection = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : a) {
            if (s == null) continue;
            String sLow = s.toLowerCase(Locale.ROOT);
            if (fuzzyContains(bLower, sLow) && seen.add(sLow)) {
                intersection.add(s);
            }
        }
        return intersection;
    }

    private static boolean fuzzyContains(Set<String> pool, String term) {
        if (pool.contains(term)) return true;
        for (String p : pool) {
            if (p.contains(term) || term.contains(p)) return true;
        }
        return false;
    }

    static List<String> splitCommaSeparated(String plain) {
        if (plain == null || plain.isBlank()) return Collections.emptyList();
        return Arrays.stream(plain.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Compute level-aware skill coverage. If candidateSkills is non-null, also gives
     * partial credit (0.5) for tag-present but unassessed skills.
     */
    static double computeLevelAwareCoverage(UUID nodeId, List<SkillWithLevel> required,
                                             List<String> candidateSkills) {
        if (required == null || required.isEmpty()) return 0;

        Map<UUID, Short> assessmentCache = null;
        Map<UUID, String> skillNameCache = null;
        if (nodeId != null) {
            List<SkillAssessment> assessments = SkillAssessment.findByNode(nodeId);
            assessmentCache = new HashMap<>();
            for (SkillAssessment a : assessments) {
                assessmentCache.put(a.skillId, a.level);
            }
            if (!assessmentCache.isEmpty()) {
                List<UUID> skillIds = new ArrayList<>(assessmentCache.keySet());
                skillNameCache = SkillDefinition.<SkillDefinition>list("id in ?1", skillIds)
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(d -> d.id, d -> d.name));
            }
        }

        double totalWeight = 0;
        double matchedWeight = 0;
        for (SkillWithLevel swl : required) {
            totalWeight += 1.0;

            boolean hasSkill = candidateSkills != null && candidateSkills.stream()
                    .anyMatch(s -> s.equalsIgnoreCase(swl.name()));

            int requiredLevel = swl.minLevel() != null ? swl.minLevel() : 0;

            if (assessmentCache != null) {
                Short candidateLevel = findAssessmentLevelByName(
                        assessmentCache, skillNameCache, swl.name());
                if (candidateLevel != null && (requiredLevel <= 0 || candidateLevel >= requiredLevel)) {
                    matchedWeight += 1.0;
                } else if (candidateLevel != null && requiredLevel > 0) {
                    matchedWeight += (double) candidateLevel / requiredLevel;
                } else if (hasSkill) {
                    matchedWeight += 0.5;
                }
            } else if (hasSkill) {
                matchedWeight += requiredLevel <= 0 ? 1.0 : 0.5;
            }
        }
        return totalWeight == 0 ? 0 : matchedWeight / totalWeight;
    }

    private static Short findAssessmentLevelByName(Map<UUID, Short> assessments,
                                                     Map<UUID, String> skillNames,
                                                     String skillName) {
        if (skillNames == null) return null;
        for (Map.Entry<UUID, Short> entry : assessments.entrySet()) {
            String name = skillNames.get(entry.getKey());
            if (name != null && name.equalsIgnoreCase(skillName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static WorkMode structuredWorkMode(MeshNode n) {
        if (n == null || n.structuredData == null) {
            return null;
        }
        Object v = n.structuredData.get("work_mode");
        return v == null ? null : parseEnum(WorkMode.class, v.toString());
    }

    static EmploymentType structuredEmploymentType(MeshNode n) {
        if (n == null || n.structuredData == null) {
            return null;
        }
        Object v = n.structuredData.get("employment_type");
        return v == null ? null : parseEnum(EmploymentType.class, v.toString());
    }

    private static final java.util.regex.Pattern MARKDOWN_FENCE =
            java.util.regex.Pattern.compile("^\\s*```(?:\\w+)?\\s*\\n?(.*?)\\n?\\s*```\\s*$",
                    java.util.regex.Pattern.DOTALL);

    static String stripMarkdownFences(String text) {
        if (text == null) return null;
        var m = MARKDOWN_FENCE.matcher(text.strip());
        return m.matches() ? m.group(1).strip() : text.strip();
    }
}
