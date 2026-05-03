package org.peoplemesh.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PmBearerAuthenticationRequestTest {

    @Test
    void accessToken_returnsConstructorValue() {
        PmBearerAuthenticationRequest request = new PmBearerAuthenticationRequest("token-1");

        assertEquals("token-1", request.accessToken());
    }
}
