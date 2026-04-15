package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HmacSignerTest {

    @Test
    void sign_sameDataAndSecret_deterministicHex() {
        String signature = HmacSigner.sign("payload", "secret");
        assertEquals(signature, HmacSigner.sign("payload", "secret"));
    }

    @Test
    void sign_differentData_differentSignature() {
        assertNotEquals(HmacSigner.sign("a", "secret"), HmacSigner.sign("b", "secret"));
    }

    @Test
    void sign_differentSecret_differentSignature() {
        assertNotEquals(HmacSigner.sign("payload", "a"), HmacSigner.sign("payload", "b"));
    }

    @Test
    void sign_outputLength_sixtyFourHexChars() {
        assertEquals(64, HmacSigner.sign("data", "key").length());
    }
}
