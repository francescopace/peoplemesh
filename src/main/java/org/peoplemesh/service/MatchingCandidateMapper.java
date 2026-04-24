package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.SqlParsingUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchingCandidateMapper {

    CandidateRow fromRepositoryRow(MeshNodeSearchRepository.UserCandidateRow row) {
        return new CandidateRow(
                row.nodeId(), row.userId(),
                SqlParsingUtils.parseEnum(Seniority.class, row.seniority()),
                row.skillsTechnical(), row.skillsSoft(), row.toolsAndTech(),
                SqlParsingUtils.parseEnum(WorkMode.class, row.workMode()),
                SqlParsingUtils.parseEnum(EmploymentType.class, row.employmentType()),
                row.learningAreas(),
                row.country(), row.timezone(), row.updatedAt(),
                row.city(),
                row.cosineSim(),
                row.displayName(), row.roles(),
                row.hobbies(), row.sports(),
                row.causes(),
                row.avatarUrl(),
                row.slackHandle(), row.email(),
                row.telegramHandle(), row.mobilePhone(),
                row.linkedinUrl()
        );
    }

    record CandidateRow(
            UUID nodeId, UUID userId, Seniority seniority,
            List<String> skillsTechnical, List<String> skillsSoft, List<String> toolsAndTech,
            WorkMode workMode, EmploymentType employmentType,
            List<String> learningAreas,
            String country, String timezone, Instant updatedAt,
            String city,
            double cosineSim,
            String displayName, String roles,
            List<String> hobbies, List<String> sports,
            List<String> causes,
            String avatarUrl,
            String slackHandle, String email,
            String telegramHandle, String mobilePhone,
            String linkedinUrl
    ) {
        List<String> combinedSkillsAndTools() {
            return MatchingUtils.combineLists(skillsTechnical, toolsAndTech);
        }
    }
}
