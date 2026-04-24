package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.repository.MeshNodeSearchRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingCandidateMapperTest {

    private final MatchingCandidateMapper mapper = new MatchingCandidateMapper();

    @Test
    void fromRepositoryRow_mapsTypedEnumsAndSkillPools() {
        UUID nodeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MeshNodeSearchRepository.UserCandidateRow row = new MeshNodeSearchRepository.UserCandidateRow(
                nodeId,
                userId,
                "SENIOR",
                List.of("Java"),
                List.of("Mentoring"),
                List.of("Docker"),
                "REMOTE",
                "EMPLOYED",
                List.of("AI"),
                "IT",
                "Europe/Rome",
                Instant.parse("2026-01-01T00:00:00Z"),
                "Rome",
                0.91,
                "Jane Doe",
                "Engineer",
                List.of("Hiking"),
                List.of("Cycling"),
                List.of("Open source"),
                "https://avatar",
                "@jane",
                "jane@example.com",
                "@jane_tg",
                "+39000",
                "https://linkedin.com/in/jane"
        );

        MatchingCandidateMapper.CandidateRow mapped = mapper.fromRepositoryRow(row);

        assertEquals(nodeId, mapped.nodeId());
        assertEquals(userId, mapped.userId());
        assertEquals(Seniority.SENIOR, mapped.seniority());
        assertEquals(WorkMode.REMOTE, mapped.workMode());
        assertEquals(EmploymentType.EMPLOYED, mapped.employmentType());
        assertEquals(List.of("Java", "Docker"), mapped.combinedSkillsAndTools());
    }
}
