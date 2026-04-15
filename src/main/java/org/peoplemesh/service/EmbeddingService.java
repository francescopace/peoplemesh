package org.peoplemesh.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmbeddingService {

    @Inject
    EmbeddingModel embeddingModel;

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return embeddingModel.embed(text).content().vector();
    }

}
