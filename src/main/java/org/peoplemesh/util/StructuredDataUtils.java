package org.peoplemesh.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for reading typed values from a structured-data map (JSONB).
 */
public final class StructuredDataUtils {

    private StructuredDataUtils() {}

    public static List<String> sdListOrEmpty(Map<String, Object> sd, String key) {
        if (sd == null) return Collections.emptyList();
        Object v = sd.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }

    public static List<String> sdListOrNull(Map<String, Object> sd, String key) {
        if (sd == null) return null;
        Object v = sd.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return null;
    }

    public static String sdString(Map<String, Object> sd, String key) {
        if (sd == null) return null;
        Object v = sd.get(key);
        return v != null ? v.toString() : null;
    }
}
