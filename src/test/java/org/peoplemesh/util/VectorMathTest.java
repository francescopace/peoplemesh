package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorMathTest {

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        float[] v = {1f, 2f, 3f};
        assertEquals(1.0, VectorMath.cosineSimilarity(v, v), 1e-9);
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, VectorMath.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_oppositeVectors_returnsNegativeOne() {
        float[] a = {1f, 0f};
        float[] b = {-1f, 0f};
        assertEquals(-1.0, VectorMath.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_mismatchedLengths_returnsZero() {
        assertEquals(0.0, VectorMath.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f}));
    }

    @Test
    void cosineSimilarity_zeroNorm_returnsZero() {
        float[] zero = {0f, 0f, 0f};
        float[] other = {1f, 2f, 3f};
        assertEquals(0.0, VectorMath.cosineSimilarity(zero, other));
    }

    @Test
    void cosineDistance_identicalVectors_returnsZero() {
        float[] v = {3f, 4f};
        assertEquals(0.0, VectorMath.cosineDistance(v, v), 1e-9);
    }

    @Test
    void cosineDistance_orthogonalVectors_returnsOne() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(1.0, VectorMath.cosineDistance(a, b), 1e-9);
    }
}
