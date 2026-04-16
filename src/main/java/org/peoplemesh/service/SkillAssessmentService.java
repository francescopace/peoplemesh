package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SkillAssessmentDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SkillAssessmentService {
    List<SkillAssessmentDto> listAssessments(UUID nodeId, UUID catalogId);

    Map<String, Integer> loadNamedLevels(UUID nodeId);
}
