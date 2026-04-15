package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillDefinition;

import java.util.List;

@ApplicationScoped
public class SystemStatisticsService {

    public SystemStatisticsDto loadStatistics() {
        long users = MeshNode.count("nodeType", NodeType.USER);
        long jobs = MeshNode.count("nodeType", NodeType.JOB);
        long groups = MeshNode.count("nodeType in ?1", List.of(NodeType.COMMUNITY, NodeType.INTEREST_GROUP));
        long skills = SkillDefinition.count();

        return new SystemStatisticsDto(users, jobs, groups, skills);
    }
}
