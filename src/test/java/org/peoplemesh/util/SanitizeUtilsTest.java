package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SanitizeUtilsTest {

    @Test
    void sanitize_null_returnsNull() {
        assertNull(SanitizeUtils.sanitize(null, 10));
    }

    @Test
    void sanitize_withinLimit_noTruncation() {
        assertEquals("abc", SanitizeUtils.sanitize("abc", 10));
    }

    @Test
    void sanitize_atExactLimit_fullStringUnchanged() {
        assertEquals("abcd", SanitizeUtils.sanitize("abcd", 4));
    }

    @Test
    void sanitize_exceedsLimit_truncatesBeforeEscaping() {
        assertEquals("ab", SanitizeUtils.sanitize("abcd", 2));
    }

    @Test
    void sanitize_htmlSpecialChars_escapedEntities() {
        assertEquals("&amp;&lt;&gt;&quot;", SanitizeUtils.sanitize("&<>\"", 100));
    }

    @Test
    void sanitize_emptyString_returnsEmpty() {
        assertEquals("", SanitizeUtils.sanitize("", 10));
    }

}
