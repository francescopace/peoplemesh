package org.peoplemesh.util;

public final class VectorMath {

    private VectorMath() {}

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    public static double cosineDistance(float[] a, float[] b) {
        return 1.0 - cosineSimilarity(a, b);
    }
}
