package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.model.MeshNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrNull;

@ApplicationScoped
public class ProfileSkillUsageService {

    @Inject
    SkillsService skillsService;

    Set<String> collectProfileSkills(MeshNode node) {
        if (node == null) {
            return Set.of();
        }
        Map<String, Object> structuredData = node.structuredData != null ? node.structuredData : Collections.emptyMap();
        List<String> all = new ArrayList<>();
        if (node.tags != null) {
            all.addAll(node.tags);
        }
        List<String> softSkills = sdListOrNull(structuredData, "skills_soft");
        if (softSkills != null) {
            all.addAll(softSkills);
        }
        List<String> tools = sdListOrNull(structuredData, "tools_and_tech");
        if (tools != null) {
            all.addAll(tools);
        }
        return skillsService.normalizeSkills(all);
    }

    Set<String> normalizeSkillFields(MeshNode node) {
        Map<String, Object> structuredData = node.structuredData != null
                ? new LinkedHashMap<>(node.structuredData)
                : new LinkedHashMap<>();
        List<String> technical = skillsService.canonicalizeSkillList(node.tags, true);
        List<String> soft = skillsService.canonicalizeSkillList(sdListOrNull(structuredData, "skills_soft"), true);
        List<String> tools = skillsService.canonicalizeSkillList(sdListOrNull(structuredData, "tools_and_tech"), true);

        if (node.tags != null) {
            node.tags = new ArrayList<>(technical);
        }
        if (structuredData.containsKey("skills_soft") || !soft.isEmpty()) {
            structuredData.put("skills_soft", soft);
        }
        if (structuredData.containsKey("tools_and_tech") || !tools.isEmpty()) {
            structuredData.put("tools_and_tech", tools);
        }
        node.structuredData = structuredData;

        Set<String> normalized = new LinkedHashSet<>();
        normalized.addAll(technical);
        normalized.addAll(soft);
        normalized.addAll(tools);
        return normalized;
    }

    void syncUsageCounters(Set<String> oldSkills, Set<String> newSkills) {
        Set<String> removed = new LinkedHashSet<>(oldSkills != null ? oldSkills : Set.of());
        removed.removeAll(newSkills != null ? newSkills : Set.of());

        Set<String> added = new LinkedHashSet<>(newSkills != null ? newSkills : Set.of());
        added.removeAll(oldSkills != null ? oldSkills : Set.of());

        skillsService.decrementUsage(removed);
        skillsService.incrementUsage(added);
    }
}
