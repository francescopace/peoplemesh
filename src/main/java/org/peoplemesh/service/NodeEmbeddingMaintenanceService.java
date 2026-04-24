package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.util.EmbeddingTextBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class NodeEmbeddingMaintenanceService {

    private static final Logger LOG = Logger.getLogger(NodeEmbeddingMaintenanceService.class);
    private static final String ACTION = "regenerate-embeddings";
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 32;
    private static final Duration STATUS_TTL = Duration.ofHours(24);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    @Inject
    NodeRepository nodeRepository;

    private final Map<UUID, MutableJobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "node-embedding-maintenance-worker");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdownExecutor() {
        jobExecutor.shutdownNow();
    }

    public EmbeddingRegenerationJobStatus startRegenerationEmbeddings(
            UUID actorId,
            NodeType nodeType,
            boolean onlyMissing,
            int batchSize
    ) {
        cleanupExpiredJobs();
        int normalizedBatchSize = normalizeBatchSize(batchSize);
        List<UUID> nodeIds = findNodeIds(nodeType, onlyMissing);

        MutableJobState job = new MutableJobState(
                UUID.randomUUID(),
                nodeType == null ? "ALL" : nodeType.name(),
                onlyMissing,
                normalizedBatchSize,
                nodeIds.size()
        );
        jobs.put(job.jobId, job);
        jobExecutor.submit(() -> runJob(actorId, nodeIds, job));
        return toStatus(job);
    }

    public Optional<EmbeddingRegenerationJobStatus> getRegenerationJobStatus(UUID jobId) {
        cleanupExpiredJobs();
        MutableJobState job = jobs.get(jobId);
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(toStatus(job));
    }

    private void runJob(UUID actorId, List<UUID> nodeIds, MutableJobState job) {
        job.markRunning();
        try {
            for (int i = 0; i < nodeIds.size(); i += job.batchSize) {
                int end = Math.min(i + job.batchSize, nodeIds.size());
                List<UUID> batchIds = nodeIds.subList(i, end);
                processBatch(batchIds, job);
            }
            job.markCompleted();
        } catch (Exception e) {
            job.markFailed(e.getMessage());
            LOG.errorf(e, "Maintenance embedding job failed: jobId=%s", job.jobId);
        } finally {
            auditRun(actorId, job);
        }
    }

    private void processBatch(List<UUID> batchIds, MutableJobState job) {
        BatchData batchData = loadBatchData(batchIds);
        if (batchData.missingCount > 0) {
            // Missing nodes are no-op and count as successful processing.
            job.recordProgress(batchData.missingCount, batchData.missingCount, 0);
        }
        if (batchData.items.isEmpty()) {
            return;
        }

        List<String> texts = batchData.items.stream()
                .map(BatchItem::text)
                .toList();
        List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
        if (embeddings.size() != batchData.items.size()) {
            throw new IllegalStateException("Embedding batch size mismatch");
        }

        persistBatchEmbeddings(batchData.items, embeddings);
        job.recordProgress(batchData.items.size(), batchData.items.size(), 0);
    }

    private BatchData loadBatchData(List<UUID> batchIds) {
        // Keep this read in its own short transaction so long-running embedding
        // generation does not pin a single transaction open for the whole job.
        return QuarkusTransaction.requiringNew().call(() -> {
            List<MeshNode> nodes = nodeRepository.findByIds(batchIds);
            Map<UUID, MeshNode> byId = nodes.stream().collect(Collectors.toMap(n -> n.id, n -> n));

            List<BatchItem> items = new ArrayList<>(batchIds.size());
            int missing = 0;
            for (UUID nodeId : batchIds) {
                MeshNode node = byId.get(nodeId);
                if (node == null) {
                    missing++;
                    continue;
                }
                items.add(new BatchItem(nodeId, EmbeddingTextBuilder.buildText(node)));
            }
            return new BatchData(items, missing);
        });
    }

    private void persistBatchEmbeddings(List<BatchItem> items, List<float[]> embeddings) {
        // Persist each batch in a fresh transaction for isolation and to avoid
        // keeping one large transaction across external embedding calls.
        QuarkusTransaction.requiringNew().run(() -> {
            List<UUID> ids = items.stream().map(BatchItem::nodeId).toList();
            List<MeshNode> nodes = nodeRepository.findByIds(ids);
            Map<UUID, MeshNode> byId = nodes.stream().collect(Collectors.toMap(n -> n.id, n -> n));

            for (int i = 0; i < items.size(); i++) {
                BatchItem item = items.get(i);
                MeshNode node = byId.get(item.nodeId);
                if (node == null) {
                    continue;
                }
                float[] embedding = embeddings.get(i);
                node.embedding = embedding;
                node.searchable = embedding != null;
                nodeRepository.persist(node);
            }
        });
    }

    private void auditRun(UUID actorId, MutableJobState job) {
        String metadata = (
                "{\"jobId\":\"%s\",\"status\":\"%s\",\"processed\":%d,\"succeeded\":%d,\"failed\":%d,"
                        + "\"nodeType\":\"%s\",\"onlyMissing\":%s,\"batchSize\":%d}"
        ).formatted(
                job.jobId,
                job.status.name(),
                job.processed.get(),
                job.succeeded.get(),
                job.failed.get(),
                job.nodeType,
                job.onlyMissing,
                job.batchSize
        );
        auditService.log(actorId, "MAINTENANCE_REGENERATE_EMBEDDINGS", "maintenance_regenerate_embeddings", null, metadata);

        LOG.infof("Maintenance embedding job completed: jobId=%s status=%s processed=%d succeeded=%d failed=%d nodeType=%s onlyMissing=%s batchSize=%d",
                job.jobId, job.status, job.processed.get(), job.succeeded.get(), job.failed.get(),
                job.nodeType, job.onlyMissing, job.batchSize);
    }

    private EmbeddingRegenerationJobStatus toStatus(MutableJobState job) {
        return new EmbeddingRegenerationJobStatus(
                ACTION,
                job.jobId,
                job.status,
                job.nodeType,
                job.onlyMissing,
                job.batchSize,
                job.total,
                job.processed.get(),
                job.succeeded.get(),
                job.failed.get(),
                job.error,
                job.createdAt,
                job.startedAt,
                job.finishedAt
        );
    }

    private static int normalizeBatchSize(int batchSize) {
        if (batchSize < MIN_BATCH_SIZE || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        return batchSize;
    }

    private void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(STATUS_TTL);
        jobs.entrySet().removeIf(entry -> {
            Instant finishedAt = entry.getValue().finishedAt;
            return finishedAt != null && finishedAt.isBefore(cutoff);
        });
    }

    List<UUID> findNodeIds(NodeType nodeType, boolean onlyMissing) {
        return nodeRepository.findNodeIds(nodeType, onlyMissing);
    }

    void regenerateSingleNodeEmbedding(UUID nodeId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Optional<MeshNode> nodeOpt = nodeRepository.findById(nodeId);
            MeshNode node = nodeOpt.isPresent() ? nodeOpt.get() : null;
            if (node == null) {
                return;
            }
            String text = EmbeddingTextBuilder.buildText(node);
            float[] embedding = embeddingService.generateEmbedding(text);
            node.embedding = embedding;
            node.searchable = embedding != null;
            nodeRepository.persist(node);
        });
    }

    public record EmbeddingRegenerationJobStatus(
            String action,
            UUID jobId,
            JobState status,
            String nodeType,
            boolean onlyMissing,
            int batchSize,
            int total,
            int processed,
            int succeeded,
            int failed,
            String error,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public enum JobState {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private record BatchData(List<BatchItem> items, int missingCount) {
    }

    private record BatchItem(UUID nodeId, String text) {
    }

    private static final class MutableJobState {
        private final UUID jobId;
        private final String nodeType;
        private final boolean onlyMissing;
        private final int batchSize;
        private final int total;
        private final Instant createdAt;
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();

        private volatile JobState status = JobState.QUEUED;
        private volatile String error;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        private MutableJobState(UUID jobId, String nodeType, boolean onlyMissing, int batchSize, int total) {
            this.jobId = jobId;
            this.nodeType = nodeType;
            this.onlyMissing = onlyMissing;
            this.batchSize = batchSize;
            this.total = total;
            this.createdAt = Instant.now();
        }

        private void markRunning() {
            this.status = JobState.RUNNING;
            this.startedAt = Instant.now();
        }

        private void markCompleted() {
            this.status = JobState.COMPLETED;
            this.finishedAt = Instant.now();
        }

        private void markFailed(String error) {
            this.status = JobState.FAILED;
            this.error = error;
            this.finishedAt = Instant.now();
        }

        private void recordProgress(int processedDelta, int succeededDelta, int failedDelta) {
            this.processed.addAndGet(processedDelta);
            this.succeeded.addAndGet(succeededDelta);
            this.failed.addAndGet(failedDelta);
        }
    }
}
