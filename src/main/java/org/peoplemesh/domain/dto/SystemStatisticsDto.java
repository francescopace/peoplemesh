package org.peoplemesh.domain.dto;

public record SystemStatisticsDto(
        long users,
        long jobs,
        long groups,
        long skills
) {}
