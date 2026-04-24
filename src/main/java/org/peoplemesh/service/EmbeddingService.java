package org.peoplemesh.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EmbeddingService {

    private static final int TARGET_VECTOR_DIMENSION = 384;

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
        Embedding embedding = embeddingModel.embed(text).content();
        return embedding == null ? null : validateVectorDimensions(embedding.vector());
    }

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

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != validIndexes.size()) {
            throw new IllegalStateException("Embedding batch response size mismatch");
        }
        for (int i = 0; i < validIndexes.size(); i++) {
            result.set(validIndexes.get(i), validateVectorDimensions(embeddings.get(i).vector()));
        }
        return result;
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
