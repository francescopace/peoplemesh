package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringUtilsTest {

    @Test
    void abbreviate_null_returnsNull() {
        assertNull(StringUtils.abbreviate(null, 5));
    }

    @Test
    void abbreviate_withinLimit_returnsUnchanged() {
        assertEquals("hi", StringUtils.abbreviate("hi", 5));
    }

    @Test
    void abbreviate_atLimit_returnsUnchanged() {
        assertEquals("hello", StringUtils.abbreviate("hello", 5));
    }

    @Test
    void abbreviate_overLimit_appendsTruncationSuffix() {
        assertEquals("hello...[truncated]", StringUtils.abbreviate("hello world", 5));
    }

    @Test
    void normalizeText_null_returnsNull() {
        assertNull(StringUtils.normalizeText(null));
    }

    @Test
    void normalizeText_blank_returnsNull() {
        assertNull(StringUtils.normalizeText(""));
    }

    @Test
    void normalizeText_whitespaceOnly_returnsNull() {
        assertNull(StringUtils.normalizeText("   \t\n  "));
    }

    @Test
    void normalizeText_leadingTrailingWhitespace_trimmedValue() {
        assertEquals("x", StringUtils.normalizeText("  x  "));
    }

    @Test
    void firstNonBlank_allNull_returnsNull() {
        assertNull(StringUtils.firstNonBlank(null, null));
    }

    @Test
    void firstNonBlank_firstBlanksThenValid_returnsFirstNonBlankTrimmed() {
        assertEquals("ok", StringUtils.firstNonBlank(null, "", "  ", "ok"));
    }

    @Test
    void firstNonBlank_firstValid_returnsFirst() {
        assertEquals("a", StringUtils.firstNonBlank("a", "b"));
    }

    @Test
    void splitCommaSeparated_blank_returnsEmpty() {
        assertTrue(StringUtils.splitCommaSeparated(" ").isEmpty());
    }

    @Test
    void splitCommaSeparated_splitsAndTrims() {
        assertEquals(java.util.List.of("a", "b", "c"), StringUtils.splitCommaSeparated("a, b , c"));
    }

    @Test
    void round3_roundsToThreeDecimals() {
        assertEquals(1.235, StringUtils.round3(1.23456));
    }

    @Test
    void stripMarkdownFences_stripsJsonFence() {
        String fenced = "```json\n{\"a\":1}\n```";
        assertEquals("{\"a\":1}", StringUtils.stripMarkdownFences(fenced));
    }
}
