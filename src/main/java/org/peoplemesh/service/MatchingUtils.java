package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared utilities for matching and search pipelines.
 * Extracted from MatchingService to avoid duplication.
 */
public final class MatchingUtils {

    private static final Set<String> EXCLUSIVE_TOKEN_PAIRS = Set.of(
            "java|javascript",
            "scala|scalability",
            "go|golang"
    );

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
        Set<String> bNormalized = b.stream()
                .filter(Objects::nonNull)
                .map(MatchingUtils::normalizeTerm)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        List<String> intersection = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : a) {
            if (s == null) continue;
            String sNormalized = normalizeTerm(s);
            if (sNormalized.isEmpty()) continue;
            if (fuzzyContains(bNormalized, sNormalized) && seen.add(sNormalized)) {
                intersection.add(s);
            }
        }
        return intersection;
    }

    static boolean termsMatch(String left, String right) {
        if (left == null || right == null) return false;
        String leftNormalized = normalizeTerm(left);
        String rightNormalized = normalizeTerm(right);
        if (leftNormalized.isEmpty() || rightNormalized.isEmpty()) return false;
        if (leftNormalized.equals(rightNormalized)) return true;

        Set<String> leftTokens = tokenSet(leftNormalized);
        Set<String> rightTokens = tokenSet(rightNormalized);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false;

        Set<String> smaller = leftTokens.size() <= rightTokens.size() ? leftTokens : rightTokens;
        Set<String> bigger = leftTokens.size() <= rightTokens.size() ? rightTokens : leftTokens;
        if (smaller.size() == 1) {
            String token = smaller.iterator().next();
            if (token.length() < 4) {
                return false;
            }
            if (((double) smaller.size() / bigger.size()) < 0.5) {
                return false;
            }
            for (String biggerToken : bigger) {
                if (isExclusivePair(token, biggerToken)) {
                    return false;
                }
            }
        }
        return bigger.containsAll(smaller);
    }

    private static boolean fuzzyContains(Set<String> pool, String term) {
        if (pool.contains(term)) return true;
        for (String p : pool) {
            if (termsMatch(p, term)) return true;
        }
        return false;
    }

    private static Set<String> tokenSet(String normalized) {
        String[] tokens = normalized.split("\\s+");
        Set<String> set = new HashSet<>();
        for (String token : tokens) {
            if (!token.isBlank()) set.add(token);
        }
        return set;
    }

    private static boolean isExclusivePair(String leftToken, String rightToken) {
        if (leftToken == null || rightToken == null || leftToken.equals(rightToken)) {
            return false;
        }
        String a = leftToken.compareTo(rightToken) <= 0 ? leftToken : rightToken;
        String b = leftToken.compareTo(rightToken) <= 0 ? rightToken : leftToken;
        return EXCLUSIVE_TOKEN_PAIRS.contains(a + "|" + b);
    }

    private static String normalizeTerm(String raw) {
        if (raw == null) return "";
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

    static List<String> splitCommaSeparated(String plain) {
        if (plain == null || plain.isBlank()) return Collections.emptyList();
        return Arrays.stream(plain.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Compute level-aware skill coverage when no per-node cached levels are available.
     */
    static double computeLevelAwareCoverage(UUID nodeId, List<SkillWithLevel> required,
                                             List<String> candidateSkills) {
        return computeLevelAwareCoverage(required, candidateSkills, null);
    }

    static double computeLevelAwareCoverage(List<SkillWithLevel> required,
                                            List<String> candidateSkills,
                                            Map<String, Short> levelsBySkillName) {
        if (required == null || required.isEmpty()) {
            return 0;
        }
        double totalWeight = 0;
        double matchedWeight = 0;
        for (SkillWithLevel swl : required) {
            totalWeight += 1.0;
            String requiredName = swl.name();
            int requiredLevel = swl.minLevel() != null ? swl.minLevel() : 0;
            Short candidateLevel = levelsBySkillName == null
                    ? null
                    : levelsBySkillName.get(normalizeTerm(requiredName));
            boolean hasSkill = candidateSkills != null && candidateSkills.stream()
                    .anyMatch(s -> s.equalsIgnoreCase(requiredName));
            if (candidateLevel != null && (requiredLevel <= 0 || candidateLevel >= requiredLevel)) {
                matchedWeight += 1.0;
            } else if (candidateLevel != null && requiredLevel > 0) {
                matchedWeight += (double) candidateLevel / requiredLevel;
            } else if (hasSkill) {
                matchedWeight += requiredLevel <= 0 ? 1.0 : 0.5;
            }
        }
        return totalWeight == 0 ? 0 : matchedWeight / totalWeight;
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
