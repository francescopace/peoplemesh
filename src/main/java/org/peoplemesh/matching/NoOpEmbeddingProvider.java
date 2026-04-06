package org.peoplemesh.matching;

import org.peoplemesh.config.AppConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.lookup.LookupIfProperty;

import java.util.Random;

/**
 * Returns deterministic pseudo-random embeddings for development/testing.
 * Active when peoplemesh.embedding.provider=noop.
 */
@ApplicationScoped
@LookupIfProperty(name = "peoplemesh.embedding.provider", stringValue = "noop")
public class NoOpEmbeddingProvider implements EmbeddingProvider {

    @Inject
    AppConfig config;

    @Override
    public float[] embed(String text) {
        int dim = config.embedding().dimension();
        float[] vector = new float[dim];
        int hash = text.hashCode();
        Random seeded = new Random(hash);
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            vector[i] = (float) seeded.nextGaussian();
            norm += vector[i] * vector[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < dim; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }

    @Override
    public int dimension() {
        return config.embedding().dimension();
    }
}
