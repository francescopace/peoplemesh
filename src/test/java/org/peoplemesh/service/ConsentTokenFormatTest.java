package org.peoplemesh.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates consent token format expectations (payload.signature structure).
 */
class ConsentTokenFormatTest {

    @Test
    void validToken_containsDotSeparator() {
        String token = "dGVzdHBheWxvYWQ.abcdef0123456789";
        assertTrue(token.contains("."));
        String[] parts = token.split("\\.", 2);
        assertEquals(2, parts.length);
        assertFalse(parts[0].isEmpty());
        assertFalse(parts[1].isEmpty());
    }

    @Test
    void invalidToken_noDotSeparator() {
        String badToken = "nodothere";
        assertFalse(badToken.contains(".") && badToken.split("\\.").length == 2);
    }
}
