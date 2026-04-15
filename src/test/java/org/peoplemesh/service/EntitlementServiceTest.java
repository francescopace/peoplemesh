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
    void canCreateJob_usesExpectedEntitlementField() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(true).when(service).hasEntitlement(nodeId, "canCreateJob");

        assertTrue(service.canCreateJob(nodeId));
        verify(service).hasEntitlement(nodeId, "canCreateJob");
    }

    @Test
    void canCreateJob_returnsFalseWhenHelperReturnsFalse() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(false).when(service).hasEntitlement(nodeId, "canCreateJob");

        assertFalse(service.canCreateJob(nodeId));
        verify(service).hasEntitlement(nodeId, "canCreateJob");
    }

    @Test
    void canManageSkills_usesExpectedEntitlementField() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(true).when(service).hasEntitlement(nodeId, "canManageSkills");

        assertTrue(service.canManageSkills(nodeId));
        verify(service).hasEntitlement(nodeId, "canManageSkills");
    }

    @Test
    void canManageSkills_returnsFalseWhenHelperReturnsFalse() {
        UUID nodeId = UUID.randomUUID();
        EntitlementService service = spy(new EntitlementService());
        doReturn(false).when(service).hasEntitlement(nodeId, "canManageSkills");

        assertFalse(service.canManageSkills(nodeId));
        verify(service).hasEntitlement(nodeId, "canManageSkills");
    }
}
