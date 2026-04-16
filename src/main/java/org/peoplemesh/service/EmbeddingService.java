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

@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

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
        return embeddingModel.embed(text).content().vector();
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
                result.set(validIndexes.get(i), embeddings.get(i).vector());
            }
            return result;
        } catch (RuntimeException e) {
            // Keep maintenance flows robust: if provider batch call fails, fall back to single inference.
            LOG.warnf("Batch embedding failed (%s), falling back to single-item embedding", e.getMessage());
            for (int i = 0; i < validIndexes.size(); i++) {
                int index = validIndexes.get(i);
                result.set(index, generateEmbedding(texts.get(index)));
            }
            return result;
        }
    }

}
