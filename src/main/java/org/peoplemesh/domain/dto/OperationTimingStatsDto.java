package org.peoplemesh.domain.dto;

public record OperationTimingStatsDto(
        long sampleCount,
        long avgMs,
        long p95Ms,
        long maxMs
) {}
