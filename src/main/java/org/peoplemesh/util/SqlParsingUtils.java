package org.peoplemesh.util;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SqlParsingUtils {

    private SqlParsingUtils() {}

    public static String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<String> parseArray(Object obj) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof String[] arr) return List.of(arr);
        if (obj instanceof java.sql.Array sqlArray) {
            try {
                Object value = sqlArray.getArray();
                if (value instanceof String[] arr) {
                    return List.of(arr);
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("null")
    public static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        throw new IllegalArgumentException("Unsupported timestamp type: " + value.getClass().getName());
    }
}
