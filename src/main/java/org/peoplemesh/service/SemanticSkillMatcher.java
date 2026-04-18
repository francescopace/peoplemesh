package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;
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

@ApplicationScoped
public class SemanticSkillMatcher {

    private static final int TOP_K = 8;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    private volatile CatalogCache catalogCache = CatalogCache.empty();

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

        List<float[]> embeddings = embeddingService.generateEmbeddings(semanticPending);
        for (int i = 0; i < semanticPending.size(); i++) {
            float[] embedding = i < embeddings.size() ? embeddings.get(i) : null;
            if (embedding == null) {
                continue;
            }
            SemanticMatch semanticMatch = findSemanticMatch(semanticPending.get(i), uniqueCandidateSkills, embedding, threshold);
            if (semanticMatch != null) {
                matches.add(semanticMatch);
            }
        }
        return matches;
    }

    private SemanticMatch findSemanticMatch(String querySkill,
                                            List<String> candidateSkills,
                                            float[] queryEmbedding,
                                            double threshold) {
        SemanticMatch dbMatch = findByRepositorySimilarity(querySkill, candidateSkills, queryEmbedding, threshold);
        if (dbMatch != null) {
            return dbMatch;
        }
        return findByCacheSimilarity(querySkill, candidateSkills, queryEmbedding, threshold);
    }

    private SemanticMatch findByRepositorySimilarity(String querySkill,
                                                     List<String> candidateSkills,
                                                     float[] queryEmbedding,
                                                     double threshold) {
        List<Object[]> similar = skillDefinitionRepository.findSimilarByEmbedding(queryEmbedding, TOP_K, threshold);
        SemanticMatch best = null;
        for (Object[] row : similar) {
            UUID skillId = (UUID) row[0];
            String skillName = row[1] != null ? row[1].toString() : null;
            List<String> aliases = MatchingUtils.parseArray(row[2]);
            double similarity = row[3] instanceof Number n ? n.doubleValue() : 0.0;
            if (skillName == null || similarity < threshold) {
                continue;
            }
            String matchedCandidate = findCandidateMatch(candidateSkills, skillName, aliases);
            if (matchedCandidate == null) {
                CatalogSkill cached = catalogSkills().byId().get(skillId);
                if (cached != null) {
                    matchedCandidate = findCandidateMatch(candidateSkills, cached.name(), cached.aliases());
                }
            }
            if (matchedCandidate == null) {
                continue;
            }
            if (best == null || similarity > best.similarity()) {
                best = new SemanticMatch(querySkill, matchedCandidate, similarity);
            }
        }
        return best;
    }

    private SemanticMatch findByCacheSimilarity(String querySkill,
                                                List<String> candidateSkills,
                                                float[] queryEmbedding,
                                                double threshold) {
        SemanticMatch best = null;
        for (CatalogSkill catalogSkill : catalogSkills().withEmbedding()) {
            if (catalogSkill.embedding() == null) {
                continue;
            }
            double similarity = VectorMath.cosineSimilarity(queryEmbedding, catalogSkill.embedding());
            if (similarity < threshold) {
                continue;
            }
            String matchedCandidate = findCandidateMatch(candidateSkills, catalogSkill.name(), catalogSkill.aliases());
            if (matchedCandidate == null) {
                continue;
            }
            if (best == null || similarity > best.similarity()) {
                best = new SemanticMatch(querySkill, matchedCandidate, similarity);
            }
        }
        return best;
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
}
