package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MatchingUtilsTest {

    // === vectorToString ===

    @Test
    void vectorToString_formats() {
        assertEquals("[1.0,2.5,3.0]", MatchingUtils.vectorToString(new float[]{1.0f, 2.5f, 3.0f}));
    }

    @Test
    void vectorToString_empty() {
        assertEquals("[]", MatchingUtils.vectorToString(new float[]{}));
    }

    @Test
    void vectorToString_single() {
        assertEquals("[7.0]", MatchingUtils.vectorToString(new float[]{7.0f}));
    }

    // === parseArray ===

    @Test
    void parseArray_null_returnsEmpty() {
        assertTrue(MatchingUtils.parseArray(null).isEmpty());
    }

    @Test
    void parseArray_stringArray_returnsList() {
        String[] arr = {"a", "b"};
        assertEquals(List.of("a", "b"), MatchingUtils.parseArray(arr));
    }

    @Test
    void parseArray_list_returnsList() {
        List<Object> list = List.of("x", 42);
        assertEquals(List.of("x", "42"), MatchingUtils.parseArray(list));
    }

    @Test
    void parseArray_listWithNulls_filtersNulls() {
        List<Object> list = new ArrayList<>(Arrays.asList("a", null, "b"));
        assertEquals(List.of("a", "b"), MatchingUtils.parseArray(list));
    }

    @Test
    void parseArray_sqlArray_success() throws SQLException {
        java.sql.Array sqlArray = new StubSqlArray(new String[]{"x", "y"});
        assertEquals(List.of("x", "y"), MatchingUtils.parseArray(sqlArray));
    }

    @Test
    void parseArray_sqlArray_exception_returnsEmpty() {
        java.sql.Array sqlArray = new StubSqlArray(null);
        assertTrue(MatchingUtils.parseArray(sqlArray).isEmpty());
    }

    @Test
    void parseArray_unknownType_returnsEmpty() {
        assertTrue(MatchingUtils.parseArray(42).isEmpty());
    }

    // === parseEnum ===

    enum Color { RED, GREEN, BLUE }

    @Test
    void parseEnum_valid() {
        assertEquals(Color.RED, MatchingUtils.parseEnum(Color.class, "RED"));
    }

    @Test
    void parseEnum_invalid_returnsNull() {
        assertNull(MatchingUtils.parseEnum(Color.class, "PURPLE"));
    }

    @Test
    void parseEnum_null_returnsNull() {
        assertNull(MatchingUtils.parseEnum(Color.class, null));
    }

    // === toInstant ===

    @Test
    void toInstant_null_returnsNull() {
        assertNull(MatchingUtils.toInstant(null));
    }

    @Test
    void toInstant_instant_returnsSame() {
        Instant now = Instant.now();
        assertSame(now, MatchingUtils.toInstant(now));
    }

    @Test
    void toInstant_timestamp_converts() {
        Instant now = Instant.now();
        java.sql.Timestamp ts = java.sql.Timestamp.from(now);
        assertEquals(now, MatchingUtils.toInstant(ts));
    }

    @Test
    void toInstant_utilDate_converts() {
        Instant now = Instant.ofEpochMilli(1000000);
        java.util.Date date = java.util.Date.from(now);
        assertEquals(now, MatchingUtils.toInstant(date));
    }

    @Test
    void toInstant_unsupportedType_throws() {
        assertThrows(IllegalArgumentException.class, () -> MatchingUtils.toInstant("not-a-date"));
    }

    // === combineLists ===

    @Test
    void combineLists_bothPresent() {
        assertEquals(List.of("a", "b", "c"), MatchingUtils.combineLists(List.of("a", "b"), List.of("c")));
    }

    @Test
    void combineLists_nullA() {
        assertEquals(List.of("x"), MatchingUtils.combineLists(null, List.of("x")));
    }

    @Test
    void combineLists_nullB() {
        assertEquals(List.of("y"), MatchingUtils.combineLists(List.of("y"), null));
    }

    @Test
    void combineLists_bothNull() {
        assertTrue(MatchingUtils.combineLists(null, null).isEmpty());
    }

    // === jaccardSimilarity ===

    @Test
    void jaccardSimilarity_identicalSets_returnsOne() {
        assertEquals(1.0, MatchingUtils.jaccardSimilarity(List.of("a", "b"), List.of("A", "B")), 1e-9);
    }

    @Test
    void jaccardSimilarity_disjointSets_returnsZero() {
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(List.of("a"), List.of("b")));
    }

    @Test
    void jaccardSimilarity_nullOrEmpty_returnsZero() {
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(null, List.of("a")));
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(List.of("a"), null));
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(List.of(), List.of("a")));
    }

    @Test
    void jaccardSimilarity_partialOverlap() {
        double sim = MatchingUtils.jaccardSimilarity(List.of("a", "b", "c"), List.of("b", "c", "d"));
        assertTrue(sim > 0.0 && sim < 1.0);
    }

    @Test
    void jaccardSimilarity_fuzzySubstring() {
        double sim = MatchingUtils.jaccardSimilarity(List.of("java"), List.of("javascript"));
        assertEquals(0.0, sim, 1e-9, "substring-only matches should not contribute");
    }

    @Test
    void jaccardSimilarity_tokenSubsetMatches() {
        double sim = MatchingUtils.jaccardSimilarity(List.of("java"), List.of("java se"));
        assertTrue(sim > 0.0, "token subset matches should contribute");
    }

    // === intersectCaseInsensitive ===

    @Test
    void intersectCaseInsensitive_caseMatch() {
        assertEquals(List.of("Java"), MatchingUtils.intersectCaseInsensitive(List.of("Java"), List.of("java")));
    }

    @Test
    void intersectCaseInsensitive_nullArgs() {
        assertTrue(MatchingUtils.intersectCaseInsensitive(null, List.of("a")).isEmpty());
        assertTrue(MatchingUtils.intersectCaseInsensitive(List.of("a"), null).isEmpty());
    }

    @Test
    void intersectCaseInsensitive_noDuplicates() {
        List<String> result = MatchingUtils.intersectCaseInsensitive(
                List.of("Java", "JAVA"), List.of("java"));
        assertEquals(1, result.size());
    }

    @Test
    void intersectCaseInsensitive_handlesNullElements() {
        List<String> a = new ArrayList<>(Arrays.asList("a", null, "b"));
        List<String> b = List.of("a");
        List<String> result = MatchingUtils.intersectCaseInsensitive(a, b);
        assertTrue(result.contains("a"));
    }

    @Test
    void intersectCaseInsensitive_doesNotMatchSubstringOnly() {
        List<String> result = MatchingUtils.intersectCaseInsensitive(List.of("Java"), List.of("JavaScript"));
        assertTrue(result.isEmpty());
    }

    @Test
    void intersectCaseInsensitive_matchesTokenSubset() {
        List<String> result = MatchingUtils.intersectCaseInsensitive(List.of("Java"), List.of("Java SE"));
        assertEquals(List.of("Java"), result);
    }

    // === splitCommaSeparated ===

    @Test
    void splitCommaSeparated_normal() {
        assertEquals(List.of("a", "b", "c"), MatchingUtils.splitCommaSeparated("a, b , c"));
    }

    @Test
    void splitCommaSeparated_nullOrBlank() {
        assertTrue(MatchingUtils.splitCommaSeparated(null).isEmpty());
        assertTrue(MatchingUtils.splitCommaSeparated("  ").isEmpty());
    }

    @Test
    void splitCommaSeparated_emptySegments() {
        assertEquals(List.of("a", "b"), MatchingUtils.splitCommaSeparated("a,,b"));
    }

    // === round3 ===

    @Test
    void round3_roundsCorrectly() {
        assertEquals(0.333, MatchingUtils.round3(0.33333333));
        assertEquals(1.0, MatchingUtils.round3(1.0));
        assertEquals(0.0, MatchingUtils.round3(0.0));
    }

    // === stripMarkdownFences ===

    @Test
    void stripMarkdownFences_null_returnsNull() {
        assertNull(MatchingUtils.stripMarkdownFences(null));
    }

    @Test
    void stripMarkdownFences_withFences_strips() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        assertEquals("{\"key\": \"value\"}", MatchingUtils.stripMarkdownFences(input));
    }

    @Test
    void stripMarkdownFences_withoutFences_returnsStripped() {
        assertEquals("hello world", MatchingUtils.stripMarkdownFences("  hello world  "));
    }

    @Test
    void stripMarkdownFences_fencesNoLang_strips() {
        String input = "```\ncontent\n```";
        assertEquals("content", MatchingUtils.stripMarkdownFences(input));
    }

    // === structuredWorkMode ===

    @Test
    void structuredWorkMode_nullNode_returnsNull() {
        assertNull(MatchingUtils.structuredWorkMode(null));
    }

    @Test
    void structuredWorkMode_nullStructuredData_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = null;
        assertNull(MatchingUtils.structuredWorkMode(node));
    }

    @Test
    void structuredWorkMode_noWorkModeKey_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = Map.of("other", "value");
        assertNull(MatchingUtils.structuredWorkMode(node));
    }

    @Test
    void structuredWorkMode_validWorkMode_returnsEnum() {
        MeshNode node = new MeshNode();
        node.structuredData = new HashMap<>(Map.of("work_mode", "REMOTE"));
        assertEquals(WorkMode.REMOTE, MatchingUtils.structuredWorkMode(node));
    }

    @Test
    void structuredWorkMode_invalidWorkMode_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = new HashMap<>(Map.of("work_mode", "INVALID"));
        assertNull(MatchingUtils.structuredWorkMode(node));
    }

    // === structuredEmploymentType ===

    @Test
    void structuredEmploymentType_nullNode_returnsNull() {
        assertNull(MatchingUtils.structuredEmploymentType(null));
    }

    @Test
    void structuredEmploymentType_nullStructuredData_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = null;
        assertNull(MatchingUtils.structuredEmploymentType(node));
    }

    @Test
    void structuredEmploymentType_noKey_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = Map.of("other", "value");
        assertNull(MatchingUtils.structuredEmploymentType(node));
    }

    @Test
    void structuredEmploymentType_validType_returnsEnum() {
        MeshNode node = new MeshNode();
        node.structuredData = new HashMap<>(Map.of("employment_type", "FREELANCE"));
        assertEquals(EmploymentType.FREELANCE, MatchingUtils.structuredEmploymentType(node));
    }

    @Test
    void structuredEmploymentType_invalidType_returnsNull() {
        MeshNode node = new MeshNode();
        node.structuredData = new HashMap<>(Map.of("employment_type", "NOPE"));
        assertNull(MatchingUtils.structuredEmploymentType(node));
    }

    /**
     * Minimal stub for java.sql.Array to test parseArray without a real DB.
     */
    private static class StubSqlArray implements java.sql.Array {
        private final Object value;
        StubSqlArray(Object value) { this.value = value; }

        @Override public String getBaseTypeName() { return "text"; }
        @Override public int getBaseType() { return java.sql.Types.VARCHAR; }
        @Override public Object getArray() throws SQLException {
            if (value == null) throw new SQLException("stub error");
            return value;
        }
        @Override public Object getArray(Map<String, Class<?>> map) { return value; }
        @Override public Object getArray(long index, int count) { return value; }
        @Override public Object getArray(long index, int count, Map<String, Class<?>> map) { return value; }
        @Override public java.sql.ResultSet getResultSet() { return null; }
        @Override public java.sql.ResultSet getResultSet(Map<String, Class<?>> map) { return null; }
        @Override public java.sql.ResultSet getResultSet(long index, int count) { return null; }
        @Override public java.sql.ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) { return null; }
        @Override public void free() {}
    }
}
