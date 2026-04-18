package org.peoplemesh.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.OperationTimingStatsDto;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.domain.dto.TimingStatisticsDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@ApplicationScoped
public class SystemStatisticsService {

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    @Inject
    EntitlementService entitlementService;

    private static final String LLM_METRIC = "peoplemesh.llm.inference";
    private static final String EMBEDDING_SINGLE_METRIC = "peoplemesh.embedding.inference";
    private static final String EMBEDDING_BATCH_METRIC = "peoplemesh.embedding.inference.batch";
    private static final List<String> HNSW_METRICS = List.of(
            "peoplemesh.hnsw.search.user",
            "peoplemesh.hnsw.search.node",
            "peoplemesh.hnsw.search.unified"
    );

    public SystemStatisticsDto loadStatistics() {
        long users = nodeRepository.countByType(NodeType.USER);
        long jobs = nodeRepository.countByType(NodeType.JOB);
        long groups = nodeRepository.countByTypes(List.of(NodeType.COMMUNITY, NodeType.INTEREST_GROUP));
        long skills = skillDefinitionRepository.countAll();
        TimingStatisticsDto timings = new TimingStatisticsDto(
                aggregateTimer(LLM_METRIC),
                aggregateTimer(EMBEDDING_SINGLE_METRIC),
                aggregateTimer(EMBEDDING_BATCH_METRIC),
                aggregateTimers(HNSW_METRICS)
        );

        return new SystemStatisticsDto(users, jobs, groups, skills, timings);
    }

    public SystemStatisticsDto loadStatisticsForUser(UUID userId) {
        if (!entitlementService.isAdmin(userId)) {
            throw new ForbiddenBusinessException("Missing entitlement is_admin");
        }
        return loadStatistics();
    }

    private OperationTimingStatsDto aggregateTimer(String metricName) {
        String safeMetricName = Objects.requireNonNull(metricName);
        return aggregateTimers(List.of(safeMetricName));
    }

    private OperationTimingStatsDto aggregateTimers(List<String> metricNames) {
        if (metricNames == null) {
            throw new IllegalArgumentException("metricNames is required");
        }
        List<Timer> timers = new ArrayList<>();
        for (String metricName : metricNames) {
            if (metricName == null || metricName.isBlank()) {
                continue;
            }
            timers.addAll(meterRegistry.find(metricName).timers());
        }
        if (timers.isEmpty()) {
            return new OperationTimingStatsDto(0, 0, 0, 0);
        }

        long sampleCount = 0L;
        double totalMs = 0.0;
        long maxMs = 0L;
        double weightedP95Sum = 0.0;
        long weightedP95Count = 0L;

        for (Timer timer : timers) {
            long timerCount = timer.count();
            sampleCount += timerCount;
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            maxMs = Math.max(maxMs, Math.round(timer.max(TimeUnit.MILLISECONDS)));
            long p95 = extractP95Ms(timer);
            if (timerCount > 0 && p95 > 0) {
                weightedP95Sum += p95 * timerCount;
                weightedP95Count += timerCount;
            }
        }

        long avgMs = sampleCount > 0 ? Math.round(totalMs / sampleCount) : 0L;
        long p95Ms = weightedP95Count > 0 ? Math.round(weightedP95Sum / weightedP95Count) : 0L;
        return new OperationTimingStatsDto(sampleCount, avgMs, p95Ms, maxMs);
    }

    private long extractP95Ms(Timer timer) {
        for (ValueAtPercentile percentileValue : timer.takeSnapshot().percentileValues()) {
            if (Math.abs(percentileValue.percentile() - 0.95d) < 0.0001d) {
                return Math.round(TimeUnit.NANOSECONDS.toMillis((long) percentileValue.value()));
            }
        }
        return 0L;
    }
}
