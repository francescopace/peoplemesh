package org.peoplemesh.matching;

/**
 * Vendor-agnostic interface for generating vector embeddings from text.
 * Implementations are selected at runtime via CDI qualifiers based on config.
 */
public interface EmbeddingProvider {

    float[] embed(String text);

    int dimension();
}
