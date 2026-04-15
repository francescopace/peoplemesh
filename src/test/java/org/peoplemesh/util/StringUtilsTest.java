package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
