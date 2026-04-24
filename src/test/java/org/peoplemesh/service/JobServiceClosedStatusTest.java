package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class JobServiceClosedStatusTest {

    @ParameterizedTest
    @ValueSource(strings = {"filled", "hired", "closed", "archived", "cancelled", "deleted",
            "FILLED", "Hired", "CLOSED"})
    void isClosedStatus_closedValues_returnsTrue(String status) throws Exception {
        assertTrue(invokeIsClosedStatus(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"published", "open", "active", "draft", "pending"})
    void isClosedStatus_openValues_returnsFalse(String status) throws Exception {
        assertFalse(invokeIsClosedStatus(status));
    }

    @Test
    void isClosedStatus_null_returnsFalse() throws Exception {
        assertFalse(invokeIsClosedStatus(null));
    }

    private boolean invokeIsClosedStatus(String status) throws Exception {
        Method method = JobService.class.getDeclaredMethod("isClosedStatus", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, status);
    }
}
