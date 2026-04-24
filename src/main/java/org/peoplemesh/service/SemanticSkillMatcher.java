package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.VectorMath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SemanticSkillMatcher {

    private static final int TOP_K = 8;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration QUERY_EMBEDDING_CACHE_TTL = Duration.ofMinutes(10);
    private static final int QUERY_EMBEDDING_CACHE_MAX_ENTRIES = 5_000;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    private volatile CatalogCache catalogCache = CatalogCache.empty();
    private final Map<String, CachedEmbedding> queryEmbeddingCache = new ConcurrentHashMap<>();

    public List<SemanticMatch> matchSkills(List<String> querySkills,
                                           List<String> candidateSkills,
                                           double threshold) {
        if (querySkills == null || querySkills.isEmpty() || candidateSkills == null || candidateSkills.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> uniqueQuerySkills = sanitize(querySkills);
        List<String> uniqueCandidateSkills = sanitize(candidateSkills);
        if (uniqueQuerySkills.isEmpty() || uniqueCandidateSkills.isEmpty()) {
            return Collections.emptyList();
        }

        List<SemanticMatch> matches = new ArrayList<>();
        List<String> semanticPending = new ArrayList<>();
        for (String querySkill : uniqueQuerySkills) {
            String exactCandidate = findExactMatch(querySkill, uniqueCandidateSkills);
            if (exactCandidate != null) {
                matches.add(new SemanticMatch(querySkill, exactCandidate, 1.0));
            } else {
                semanticPending.add(querySkill);
            }
        }
        if (semanticPending.isEmpty()) {
            return matches;
        }
        Map<String, List<String>> resolvedOptions = resolveSkillNeighbors(semanticPending, threshold);
        matches.addAll(matchSkillsWithResolvedOptions(semanticPending, uniqueCandidateSkills, resolvedOptions));
        return matches;
    }

    public Map<String, List<String>> resolveSkillNeighbors(List<String> querySkills, double threshold) {
        if (querySkills == null || querySkills.isEmpty()) {
            return Map.of();
        }
        List<String> uniqueQuerySkills = sanitize(querySkills);
        if (uniqueQuerySkills.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> resolved = new LinkedHashMap<>();
        List<float[]> embeddings = loadQueryEmbeddings(uniqueQuerySkills);
        for (int i = 0; i < uniqueQuerySkills.size(); i++) {
            String querySkill = uniqueQuerySkills.get(i);
            float[] embedding = i < embeddings.size() ? embeddings.get(i) : null;
            LinkedHashMap<String, String> optionsByNormalized = new LinkedHashMap<>();
            addOption(optionsByNormalized, querySkill);
            if (embedding != null) {
                List<SkillDefinitionRepository.SimilarSkillRow> similar = skillDefinitionRepository
                        .findSimilarByEmbedding(embedding, TOP_K, threshold);
                for (SkillDefinitionRepository.SimilarSkillRow row : similar) {
                    if (row == null) {
                        continue;
                    }
                    if (row.cosineSimilarity() < threshold) {
                        continue;
                    }
                    addOption(optionsByNormalized, row.skillName());
                    if (row.aliases() != null) {
                        for (String alias : row.aliases()) {
                            addOption(optionsByNormalized, alias);
                        }
                    }
                    CatalogSkill cached = catalogSkills().byId().get(row.skillId());
                    if (cached != null) {
                        addOption(optionsByNormalized, cached.name());
                        if (cached.aliases() != null) {
                            for (String alias : cached.aliases()) {
                                addOption(optionsByNormalized, alias);
                            }
                        }
                    }
                }
                if (optionsByNormalized.size() <= 1) {
                    for (CatalogSkill catalogSkill : catalogSkills().withEmbedding()) {
                        if (catalogSkill.embedding() == null) {
                            continue;
                        }
                        double similarity = VectorMath.cosineSimilarity(embedding, catalogSkill.embedding());
                        if (similarity < threshold) {
                            continue;
                        }
                        addOption(optionsByNormalized, catalogSkill.name());
                        if (catalogSkill.aliases() != null) {
                            for (String alias : catalogSkill.aliases()) {
                                addOption(optionsByNormalized, alias);
                            }
                        }
                    }
                }
            }
            resolved.put(querySkill, new ArrayList<>(optionsByNormalized.values()));
        }
        return resolved;
    }

    public List<SemanticMatch> matchSkillsWithResolvedOptions(
            List<String> querySkills,
            List<String> candidateSkills,
            Map<String, List<String>> resolvedOptions) {
        if (querySkills == null || querySkills.isEmpty() || candidateSkills == null || candidateSkills.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> uniqueQuerySkills = sanitize(querySkills);
        List<String> uniqueCandidateSkills = sanitize(candidateSkills);
        if (uniqueQuerySkills.isEmpty() || uniqueCandidateSkills.isEmpty()) {
            return Collections.emptyList();
        }
        List<SemanticMatch> matches = new ArrayList<>();
        for (String querySkill : uniqueQuerySkills) {
            String exactCandidate = findExactMatch(querySkill, uniqueCandidateSkills);
            if (exactCandidate != null) {
                matches.add(new SemanticMatch(querySkill, exactCandidate, 1.0));
                continue;
            }
            List<String> options = resolvedOptions != null ? resolvedOptions.get(querySkill) : null;
            if (options == null || options.isEmpty()) {
                continue;
            }
            String matchedCandidate = findCandidateMatch(uniqueCandidateSkills, null, options);
            if (matchedCandidate != null) {
                matches.add(new SemanticMatch(querySkill, matchedCandidate, 1.0));
            }
        }
        return matches;
    }

    private String findCandidateMatch(List<String> candidateSkills, String skillName, List<String> aliases) {
        List<String> options = new ArrayList<>();
        if (skillName != null && !skillName.isBlank()) {
            options.add(skillName);
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    options.add(alias);
                }
            }
        }
        for (String candidate : candidateSkills) {
            for (String option : options) {
                if (MatchingUtils.termsMatch(option, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String findExactMatch(String querySkill, List<String> candidateSkills) {
        for (String candidateSkill : candidateSkills) {
            if (MatchingUtils.termsMatch(querySkill, candidateSkill)) {
                return candidateSkill;
            }
        }
        return null;
    }

    private CatalogCache catalogSkills() {
        CatalogCache current = catalogCache;
        if (!current.isExpired()) {
            return current;
        }
        synchronized (this) {
            current = catalogCache;
            if (!current.isExpired()) {
                return current;
            }
            Map<UUID, CatalogSkill> byId = new LinkedHashMap<>();
            List<CatalogSkill> withEmbedding = new ArrayList<>();
            for (SkillDefinition definition : skillDefinitionRepository.findAll()) {
                if (definition == null || definition.id == null || definition.name == null || definition.name.isBlank()) {
                    continue;
                }
                List<String> aliases = definition.aliases == null ? List.of() : List.copyOf(definition.aliases);
                CatalogSkill skill = new CatalogSkill(definition.id, definition.name, aliases, definition.embedding);
                byId.put(definition.id, skill);
                if (definition.embedding != null) {
                    withEmbedding.add(skill);
                }
            }
            catalogCache = new CatalogCache(Instant.now(), byId, withEmbedding);
            return catalogCache;
        }
    }

    private static List<String> sanitize(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> cleaned = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }

    private List<float[]> loadQueryEmbeddings(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptyList();
        }
        Instant now = Instant.now();
        List<float[]> embeddings = new ArrayList<>(Collections.nCopies(terms.size(), null));
        List<String> missingTerms = new ArrayList<>();
        List<Integer> missingIndexes = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            String key = normalizeCacheKey(term);
            CachedEmbedding cached = queryEmbeddingCache.get(key);
            if (cached != null && !cached.isExpired(now)) {
                embeddings.set(i, cached.embedding());
                continue;
            }
            if (cached != null) {
                queryEmbeddingCache.remove(key);
            }
            missingTerms.add(term);
            missingIndexes.add(i);
        }
        if (!missingTerms.isEmpty()) {
            List<float[]> generated = embeddingService.generateEmbeddings(missingTerms);
            for (int j = 0; j < missingIndexes.size(); j++) {
                int targetIndex = missingIndexes.get(j);
                float[] embedding = j < generated.size() ? generated.get(j) : null;
                embeddings.set(targetIndex, embedding);
                if (embedding != null) {
                    queryEmbeddingCache.put(normalizeCacheKey(terms.get(targetIndex)), new CachedEmbedding(now, embedding));
                }
            }
            pruneQueryEmbeddingCache(now);
        }
        return embeddings;
    }

    private void pruneQueryEmbeddingCache(Instant now) {
        for (Map.Entry<String, CachedEmbedding> entry : queryEmbeddingCache.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isExpired(now)) {
                queryEmbeddingCache.remove(entry.getKey(), entry.getValue());
            }
        }
        if (queryEmbeddingCache.size() <= QUERY_EMBEDDING_CACHE_MAX_ENTRIES) {
            return;
        }
        int toDrop = queryEmbeddingCache.size() - QUERY_EMBEDDING_CACHE_MAX_ENTRIES;
        for (String key : queryEmbeddingCache.keySet()) {
            if (toDrop <= 0) {
                break;
            }
            if (queryEmbeddingCache.remove(key) != null) {
                toDrop--;
            }
        }
    }

    private static String normalizeCacheKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void addOption(Map<String, String> optionsByNormalized, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        String normalized = MatchingUtils.normalizeTerm(trimmed);
        if (normalized.isEmpty()) {
            return;
        }
        optionsByNormalized.putIfAbsent(normalized, trimmed);
    }

    public record SemanticMatch(String querySkill, String matchedSkill, double similarity) {
    }

    private record CatalogSkill(UUID id, String name, List<String> aliases, float[] embedding) {
    }

    private record CatalogCache(Instant loadedAt, Map<UUID, CatalogSkill> byId, List<CatalogSkill> withEmbedding) {
        static CatalogCache empty() {
            return new CatalogCache(Instant.EPOCH, Collections.emptyMap(), Collections.emptyList());
        }

        boolean isExpired() {
            return loadedAt == null || loadedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }

    private record CachedEmbedding(Instant loadedAt, float[] embedding) {
        boolean isExpired(Instant now) {
            return loadedAt == null || loadedAt.plus(QUERY_EMBEDDING_CACHE_TTL).isBefore(now);
        }
    }
}
