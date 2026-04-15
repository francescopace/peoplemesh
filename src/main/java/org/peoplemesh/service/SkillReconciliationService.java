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
                float[] tagEmbedding = embeddingService.generateEmbedding(tag);
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
                LOG.debugf("Failed to generate embedding for tag '%s': %s", tag, e.getMessage());
            }
        }

        return results;
    }

    MeshNode loadNode(UUID nodeId) {
        return MeshNode.findById(nodeId);
    }

    List<SkillDefinition> loadCatalogSkills(UUID catalogId) {
        if (catalogId == null) {
            return SkillDefinition.listAll();
        }
        return SkillDefinition.findByCatalog(catalogId);
    }

    Set<UUID> loadExistingAssessmentIds(UUID nodeId) {
        return SkillAssessment.findByNode(nodeId).stream()
                .map(a -> a.skillId)
                .collect(Collectors.toSet());
    }

    double reconciliationThreshold() {
        return config.skills().reconciliationThreshold();
    }

    @Transactional
    public void applyReconciliation(UUID nodeId, UUID userId, List<SkillAssessmentDto> assessments) {
        MeshNode.findByIdOptional(nodeId)
                .filter(n -> ((MeshNode) n).createdBy.equals(userId))
                .orElseThrow(() -> new SecurityException("Not authorized for this node"));
        for (SkillAssessmentDto dto : assessments) {
            if (dto.skillId() == null) continue;

            SkillAssessment existing = SkillAssessment.find(
                    "nodeId = ?1 and skillId = ?2", nodeId, dto.skillId()
            ).firstResult();

            if (existing != null) {
                existing.level = (short) dto.level();
                existing.interest = dto.interest();
                existing.assessedAt = Instant.now();
                if (dto.source() != null) existing.source = dto.source();
            } else {
                SkillAssessment a = new SkillAssessment();
                a.nodeId = nodeId;
                a.skillId = dto.skillId();
                a.level = (short) dto.level();
                a.interest = dto.interest();
                a.source = dto.source() != null ? dto.source() : "SELF";
                a.assessedAt = Instant.now();
                a.persist();
            }
        }
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        return org.peoplemesh.util.VectorMath.cosineSimilarity(a, b);
    }
}
