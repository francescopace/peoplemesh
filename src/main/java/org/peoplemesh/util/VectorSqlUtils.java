package org.peoplemesh.util;

public final class VectorSqlUtils {
    private VectorSqlUtils() {
    }

    public static String vectorToSqlLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
