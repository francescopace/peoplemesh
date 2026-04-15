package org.peoplemesh.domain.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitExceptionTest {

    @Test
    void constructor_setsMessage() {
        RateLimitException ex = new RateLimitException("Too many requests");
        assertEquals("Too many requests", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(RateLimitException.class));
    }
}
