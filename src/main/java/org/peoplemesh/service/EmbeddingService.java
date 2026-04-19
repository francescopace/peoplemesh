package org.peoplemesh.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);
    private static final int TARGET_VECTOR_DIMENSION = 384;
    private static final int[] EMBEDDING_TEXT_LIMITS = {3000, 2000, 1400, 1000, 700, 500, 350};
    private static final int MAX_ATTEMPTS_PER_LIMIT = 3;
    private static final long RETRY_BASE_DELAY_MS = 250L;

    @Inject
    EmbeddingModel embeddingModel;

    @Timed(
            value = "peoplemesh.embedding.inference",
            description = "Embedding inference latency",
            percentiles = {0.95},
            histogram = true
    )
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return generateEmbeddingWithFallback(text);
    }

    @Timed(
            value = "peoplemesh.embedding.inference.batch",
            description = "Embedding batch inference latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> validIndexes = new ArrayList<>(texts.size());
        List<TextSegment> segments = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            result.add(null);
            if (text == null || text.isBlank()) {
                continue;
            }
            validIndexes.add(i);
            segments.add(TextSegment.from(text));
        }

        if (segments.isEmpty()) {
            return result;
        }

        try {
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();
            if (embeddings == null || embeddings.size() != validIndexes.size()) {
                throw new IllegalStateException("Embedding batch response size mismatch");
            }
            for (int i = 0; i < validIndexes.size(); i++) {
                result.set(validIndexes.get(i), validateVectorDimensions(embeddings.get(i).vector()));
            }
            return result;
        } catch (RuntimeException e) {
            // Keep maintenance flows robust: if provider batch call fails, fall back to single inference.
            LOG.warnf("Batch embedding failed (%s), falling back to single-item embedding", e.getMessage());
            for (int i = 0; i < validIndexes.size(); i++) {
                int index = validIndexes.get(i);
                result.set(index, generateEmbeddingWithFallback(texts.get(index)));
            }
            return result;
        }
    }

    private float[] generateEmbeddingWithFallback(String text) {
        RuntimeException lastContextOverflow = null;
        for (String candidate : buildCandidateTexts(text)) {
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LIMIT; attempt++) {
                try {
                    Embedding embedding = embeddingModel.embed(candidate).content();
                    return embedding == null ? null : validateVectorDimensions(embedding.vector());
                } catch (RuntimeException e) {
                    if (isContextLengthError(e)) {
                        lastContextOverflow = e;
                        break;
                    }
                    if (isRetryableError(e) && attempt < MAX_ATTEMPTS_PER_LIMIT - 1) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw e;
                }
            }
        }
        if (lastContextOverflow != null) {
            throw lastContextOverflow;
        }
        return null;
    }

    private static List<String> buildCandidateTexts(String text) {
        List<String> candidates = new ArrayList<>();
        candidates.add(text);
        for (int limit : EMBEDDING_TEXT_LIMITS) {
            if (text.length() <= limit) {
                continue;
            }
            String shortened = text.substring(0, limit);
            if (!candidates.get(candidates.size() - 1).equals(shortened)) {
                candidates.add(shortened);
            }
        }
        return candidates;
    }

    private static boolean isContextLengthError(RuntimeException e) {
        String msg = flattenMessages(e);
        return msg.contains("input length exceeds")
                || msg.contains("context length")
                || msg.contains("maximum context length")
                || msg.contains("too many tokens");
    }

    private static boolean isRetryableError(RuntimeException e) {
        String msg = flattenMessages(e);
        return msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("connection reset")
                || msg.contains("connection refused")
                || msg.contains("temporarily unavailable")
                || msg.contains("rate limit")
                || msg.contains("http 429")
                || msg.contains("http 502")
                || msg.contains("http 503")
                || msg.contains("http 504");
    }

    private static String flattenMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(cursor.getMessage());
            }
            cursor = cursor.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void sleepBeforeRetry(int attempt) {
        long sleepMs = RETRY_BASE_DELAY_MS * (1L << attempt);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding retry interrupted", ie);
        }
    }

    private static float[] validateVectorDimensions(float[] vector) {
        if (vector == null) {
            return null;
        }
        if (vector.length == TARGET_VECTOR_DIMENSION) {
            return vector;
        }
        throw new IllegalStateException(
                "Embedding dimension mismatch: expected "
                        + TARGET_VECTOR_DIMENSION
                        + ", got "
                        + vector.length
        );
    }

}
