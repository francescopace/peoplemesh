package org.peoplemesh.domain.dto;

public record TimingStatisticsDto(
        OperationTimingStatsDto llmInference,
        OperationTimingStatsDto embeddingInferenceSingle,
        OperationTimingStatsDto embeddingInferenceBatch,
        OperationTimingStatsDto hnswSearch
) {}
