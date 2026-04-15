package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.domain.model.SkillDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SkillAssessmentHelper {

    private SkillAssessmentHelper() {}

    public static List<SkillAssessmentDto> listAssessments(UUID nodeId, UUID catalogId) {
        List<SkillAssessment> assessments = SkillAssessment.findByNode(nodeId);
        List<SkillAssessmentDto> result = new ArrayList<>();
        for (SkillAssessment a : assessments) {
            SkillDefinition sd = SkillDefinition.findById(a.skillId);
            if (sd == null) continue;
            if (catalogId != null && !catalogId.equals(sd.catalogId)) continue;
            result.add(SkillAssessmentDto.fromAssessment(
                    a.skillId, sd.name, sd.category,
                    a.level, a.interest, a.source));
        }
        return result;
    }
}
