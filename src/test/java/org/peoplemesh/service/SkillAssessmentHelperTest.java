package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.model.SkillAssessment;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillAssessmentHelperTest {

    @Test
    void listAssessments_emptyAssessments_returnsEmpty() {
        UUID nodeId = UUID.randomUUID();
        try (var mocked = mockStatic(SkillAssessment.class)) {
            mocked.when(() -> SkillAssessment.findByNode(nodeId))
                    .thenReturn(Collections.emptyList());

            assertTrue(SkillAssessmentHelper.listAssessments(nodeId, null).isEmpty());
        }
    }

    @Test
    void listAssessments_emptyAssessments_withCatalogFilter_returnsEmpty() {
        UUID nodeId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        try (var mocked = mockStatic(SkillAssessment.class)) {
            mocked.when(() -> SkillAssessment.findByNode(nodeId))
                    .thenReturn(Collections.emptyList());

            assertTrue(SkillAssessmentHelper.listAssessments(nodeId, catalogId).isEmpty());
        }
    }
}
