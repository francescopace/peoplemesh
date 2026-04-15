package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuredDataUtilsTest {

    @Test
    void sdListOrEmpty_nullMap_returnsEmpty() {
        assertEquals(Collections.emptyList(), StructuredDataUtils.sdListOrEmpty(null, "key"));
    }

    @Test
    void sdListOrEmpty_missingKey_returnsEmpty() {
        assertEquals(Collections.emptyList(), StructuredDataUtils.sdListOrEmpty(Map.of(), "absent"));
    }

    @Test
    void sdListOrEmpty_nonListValue_returnsEmpty() {
        assertEquals(Collections.emptyList(), StructuredDataUtils.sdListOrEmpty(Map.of("k", "scalar"), "k"));
    }

    @Test
    void sdListOrEmpty_listValue_returnsStringList() {
        Map<String, Object> sd = Map.of("skills", List.of("Java", "Quarkus"));
        assertEquals(List.of("Java", "Quarkus"), StructuredDataUtils.sdListOrEmpty(sd, "skills"));
    }

    @Test
    void sdListOrEmpty_integerList_convertsToString() {
        Map<String, Object> sd = Map.of("nums", List.of(1, 2, 3));
        assertEquals(List.of("1", "2", "3"), StructuredDataUtils.sdListOrEmpty(sd, "nums"));
    }

    @Test
    void sdListOrNull_nullMap_returnsNull() {
        assertNull(StructuredDataUtils.sdListOrNull(null, "key"));
    }

    @Test
    void sdListOrNull_missingKey_returnsNull() {
        assertNull(StructuredDataUtils.sdListOrNull(Map.of(), "absent"));
    }

    @Test
    void sdListOrNull_nonListValue_returnsNull() {
        assertNull(StructuredDataUtils.sdListOrNull(Map.of("k", 42), "k"));
    }

    @Test
    void sdListOrNull_listValue_returnsStringList() {
        Map<String, Object> sd = Map.of("tags", List.of("a", "b"));
        assertEquals(List.of("a", "b"), StructuredDataUtils.sdListOrNull(sd, "tags"));
    }

    @Test
    void sdString_nullMap_returnsNull() {
        assertNull(StructuredDataUtils.sdString(null, "key"));
    }

    @Test
    void sdString_missingKey_returnsNull() {
        assertNull(StructuredDataUtils.sdString(Map.of(), "absent"));
    }

    @Test
    void sdString_presentValue_returnsString() {
        assertEquals("REMOTE", StructuredDataUtils.sdString(Map.of("mode", "REMOTE"), "mode"));
    }

    @Test
    void sdString_numericValue_returnsToString() {
        assertEquals("42", StructuredDataUtils.sdString(Map.of("count", 42), "count"));
    }
}
