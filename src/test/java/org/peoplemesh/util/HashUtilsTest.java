package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashUtilsTest {

    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    void sha256_knownVector_matchesExpectedHex() {
        assertEquals(HELLO_SHA256, HashUtils.sha256("hello"));
    }

    @Test
    void sha256_repeatedCalls_sameResult() {
        String once = HashUtils.sha256("payload");
        assertEquals(once, HashUtils.sha256("payload"));
        assertEquals(once, HashUtils.sha256("payload"));
    }

    @Test
    void sha256_differentInputs_differentHashes() {
        assertNotEquals(HashUtils.sha256("a"), HashUtils.sha256("b"));
    }
}
