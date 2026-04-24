package org.peoplemesh.domain.dto;

public record SystemStatisticsDto(
        long users,
        long jobs,
        long others,
        long skills,
        long searchableNodes,
        long searchableNodesWithEmbedding,
        TimingStatisticsDto timings
) {}
