package org.peoplemesh.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class EntitlementServiceTest {

    @Test
    void isAdmin_usesExpectedEntitlementField() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(true).when(service).hasEntitlement(nodeId, "isAdmin");

        assertTrue(service.isAdmin(nodeId));
        verify(service).hasEntitlement(nodeId, "isAdmin");
    }

    @Test
    void isAdmin_returnsFalseWhenHelperReturnsFalse() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(false).when(service).hasEntitlement(nodeId, "isAdmin");

        assertFalse(service.isAdmin(nodeId));
        verify(service).hasEntitlement(nodeId, "isAdmin");
    }
}
