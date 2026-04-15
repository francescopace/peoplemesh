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
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class SystemStatisticsService {

    @Inject
    MeterRegistry meterRegistry;

    private static final String LLM_METRIC = "peoplemesh.llm.inference";
    private static final String EMBEDDING_METRIC = "peoplemesh.embedding.inference";
    private static final String HNSW_METRIC = "peoplemesh.hnsw.search";

    public SystemStatisticsDto loadStatistics() {
        long users = MeshNode.count("nodeType", NodeType.USER);
        long jobs = MeshNode.count("nodeType", NodeType.JOB);
        long groups = MeshNode.count("nodeType in ?1", List.of(NodeType.COMMUNITY, NodeType.INTEREST_GROUP));
        long skills = SkillDefinition.count();
        TimingStatisticsDto timings = new TimingStatisticsDto(
                aggregateTimer(LLM_METRIC),
                aggregateTimer(EMBEDDING_METRIC),
                aggregateTimer(HNSW_METRIC)
        );

        return new SystemStatisticsDto(users, jobs, groups, skills, timings);
    }

    private OperationTimingStatsDto aggregateTimer(String metricName) {
        String safeMetricName = Objects.requireNonNull(metricName);
        Collection<Timer> timers = meterRegistry.find(safeMetricName).timers();
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
