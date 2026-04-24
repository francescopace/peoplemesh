package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlParsingUtilsTest {

    @Test
    void vectorToString_formatsValues() {
        assertEquals("[1.0,2.5,3.0]", SqlParsingUtils.vectorToString(new float[]{1.0f, 2.5f, 3.0f}));
    }

    @Test
    void parseArray_handlesSqlArray() throws SQLException {
        java.sql.Array sqlArray = new StubSqlArray(new String[]{"a", "b"});
        assertEquals(List.of("a", "b"), SqlParsingUtils.parseArray(sqlArray));
    }

    @Test
    void parseArray_handlesList() {
        assertEquals(List.of("x", "42"), SqlParsingUtils.parseArray(List.of("x", 42)));
    }

    @Test
    void parseEnum_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> SqlParsingUtils.parseEnum(TestColor.class, "NOPE"));
    }

    @Test
    void toInstant_handlesTimestampAndDate() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(now, SqlParsingUtils.toInstant(Timestamp.from(now)));
        assertEquals(now, SqlParsingUtils.toInstant(Date.from(now)));
    }

    @Test
    void toInstant_withInstant_returnsSameReference() {
        Instant now = Instant.now();
        assertSame(now, SqlParsingUtils.toInstant(now));
    }

    @Test
    void toInstant_unsupportedType_throws() {
        assertThrows(IllegalArgumentException.class, () -> SqlParsingUtils.toInstant(Map.of()));
    }

    private enum TestColor { RED }

    private static class StubSqlArray implements java.sql.Array {
        private final Object value;

        StubSqlArray(Object value) {
            this.value = value;
        }

        @Override
        public String getBaseTypeName() {
            return "text";
        }

        @Override
        public int getBaseType() {
            return java.sql.Types.VARCHAR;
        }

        @Override
        public Object getArray() throws SQLException {
            if (value == null) {
                throw new SQLException("stub error");
            }
            return value;
        }

        @Override
        public Object getArray(Map<String, Class<?>> map) {
            return value;
        }

        @Override
        public Object getArray(long index, int count) {
            return value;
        }

        @Override
        public Object getArray(long index, int count, Map<String, Class<?>> map) {
            return value;
        }

        @Override
        public java.sql.ResultSet getResultSet() {
            return null;
        }

        @Override
        public java.sql.ResultSet getResultSet(Map<String, Class<?>> map) {
            return null;
        }

        @Override
        public java.sql.ResultSet getResultSet(long index, int count) {
            return null;
        }

        @Override
        public java.sql.ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) {
            return null;
        }

        @Override
        public void free() {}
    }
}
