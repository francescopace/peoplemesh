package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class EntitlementServiceTest {

    @Test
    void isAdmin_usesExpectedEntitlementField() {
        UUID nodeId = UUID.randomUUID();
        UserIdentityRepository repository = mock(UserIdentityRepository.class);
        when(repository.hasAdminEntitlement(nodeId)).thenReturn(true);
        EntitlementService service = new EntitlementService();
        service.userIdentityRepository = repository;

        assertTrue(service.isAdmin(nodeId));
        verify(repository).hasAdminEntitlement(nodeId);
    }

    @Test
    void isAdmin_returnsFalseWhenHelperReturnsFalse() {
        UUID nodeId = UUID.randomUUID();
        UserIdentityRepository repository = mock(UserIdentityRepository.class);
        when(repository.hasAdminEntitlement(nodeId)).thenReturn(false);
        EntitlementService service = new EntitlementService();
        service.userIdentityRepository = repository;

        assertFalse(service.isAdmin(nodeId));
        verify(repository).hasAdminEntitlement(nodeId);
    }
}
