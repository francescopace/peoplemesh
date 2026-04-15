package org.peoplemesh.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmbeddingService {

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

}
