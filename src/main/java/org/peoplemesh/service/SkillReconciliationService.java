package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.SkillAssessmentRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillReconciliationService {

    private static final Logger LOG = Logger.getLogger(SkillReconciliationService.class);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AppConfig config;

    @Inject
    SkillAssessmentRepository skillAssessmentRepository;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    /**
     * Reconcile a node's free-text tags against a skill catalog.
     * Returns suggestions with match type and confidence.
     */
    public List<SkillAssessmentDto> reconcile(UUID nodeId, UUID catalogId) {
        MeshNode node = loadNode(nodeId);
        if (node == null || node.tags == null || node.tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<SkillDefinition> catalogSkills = loadCatalogSkills(catalogId);
        if (catalogSkills.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SkillDefinition> nameIndex = new HashMap<>();
        Map<String, SkillDefinition> aliasIndex = new HashMap<>();
        for (SkillDefinition sd : catalogSkills) {
            nameIndex.put(sd.name.toLowerCase(Locale.ROOT), sd);
            if (sd.aliases != null) {
                for (String alias : sd.aliases) {
                    aliasIndex.put(alias.toLowerCase(Locale.ROOT), sd);
                }
            }
        }

        Set<UUID> existingAssessments = loadExistingAssessmentIds(nodeId);

        double threshold = reconciliationThreshold();
        List<SkillAssessmentDto> results = new ArrayList<>();
        Map<String, float[]> embeddingCache = new HashMap<>();

        for (String tag : node.tags) {
            String tagLower = tag.toLowerCase(Locale.ROOT);

            SkillDefinition exactMatch = nameIndex.get(tagLower);
            if (exactMatch == null) {
                exactMatch = aliasIndex.get(tagLower);
            }

            if (exactMatch != null) {
                if (!existingAssessments.contains(exactMatch.id)) {
                    results.add(SkillAssessmentDto.suggestion(
                            exactMatch.id, exactMatch.name, exactMatch.category,
                            "EXACT", 1.0));
                }
                continue;
            }

            try {
                float[] tagEmbedding = embeddingCache.computeIfAbsent(tagLower, k -> embeddingService.generateEmbedding(tag));
                SkillDefinition bestMatch = null;
                double bestSim = 0;

                for (SkillDefinition sd : catalogSkills) {
                    if (sd.embedding == null) continue;
                    double sim = cosineSimilarity(tagEmbedding, sd.embedding);
                    if (sim > bestSim) {
                        bestSim = sim;
                        bestMatch = sd;
                    }
                }

                if (bestMatch != null && bestSim >= threshold && !existingAssessments.contains(bestMatch.id)) {
                    results.add(SkillAssessmentDto.suggestion(
                            bestMatch.id, bestMatch.name, bestMatch.category,
                            "FUZZY", Math.round(bestSim * 1000.0) / 1000.0));
                }
            } catch (Exception e) {
                LOG.debugf(e, "Failed to generate embedding for nodeId=%s", nodeId);
            }
        }

        return results;
    }

    MeshNode loadNode(UUID nodeId) {
        return nodeRepository.findById(nodeId).orElse(null);
    }

    List<SkillDefinition> loadCatalogSkills(UUID catalogId) {
        if (catalogId == null) {
            return skillDefinitionRepository.findAll();
        }
        return skillDefinitionRepository.findByCatalog(catalogId);
    }

    Set<UUID> loadExistingAssessmentIds(UUID nodeId) {
        return skillAssessmentRepository.findByNode(nodeId).stream()
                .map(a -> a.skillId)
                .collect(Collectors.toSet());
    }

    double reconciliationThreshold() {
        return config.skills().reconciliationThreshold();
    }

    @Transactional
    public void applyReconciliation(UUID nodeId, UUID userId, List<SkillAssessmentDto> assessments) {
        nodeRepository.findById(nodeId)
                .filter(n -> n.createdBy.equals(userId))
                .orElseThrow(() -> new SecurityException("Not authorized for this node"));
        Map<UUID, SkillAssessment> existingBySkill = skillAssessmentRepository.findByNodeAsMap(nodeId);
        Instant now = Instant.now();
        for (SkillAssessmentDto dto : assessments) {
            if (dto.skillId() == null) continue;

            SkillAssessment existing = existingBySkill.get(dto.skillId());

            if (existing != null) {
                existing.level = (short) dto.level();
                existing.interest = dto.interest();
                existing.assessedAt = now;
                if (dto.source() != null) existing.source = dto.source();
            } else {
                SkillAssessment a = new SkillAssessment();
                a.nodeId = nodeId;
                a.skillId = dto.skillId();
                a.level = (short) dto.level();
                a.interest = dto.interest();
                a.source = dto.source() != null ? dto.source() : "SELF";
                a.assessedAt = now;
                skillAssessmentRepository.persist(a);
                existingBySkill.put(dto.skillId(), a);
            }
        }
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        return org.peoplemesh.util.VectorMath.cosineSimilarity(a, b);
    }
}
