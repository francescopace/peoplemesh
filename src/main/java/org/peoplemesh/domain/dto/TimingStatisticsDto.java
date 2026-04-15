package org.peoplemesh.domain.dto;

public record TimingStatisticsDto(
        OperationTimingStatsDto llmInference,
        OperationTimingStatsDto embeddingInference,
        OperationTimingStatsDto hnswSearch
) {}
