package org.peoplemesh.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PmSessionAuthenticationRequestTest {

    @Test
    void cookieValue_returnsConstructorArg() {
        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest("test-cookie");
        assertEquals("test-cookie", request.cookieValue());
    }

    @Test
    void cookieValue_nullArg_returnsNull() {
        PmSessionAuthenticationRequest request = new PmSessionAuthenticationRequest(null);
        assertNull(request.cookieValue());
    }
}
