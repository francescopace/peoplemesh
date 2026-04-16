package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.UUID;

@ApplicationScoped
public class EntitlementService {

    @Inject
    UserIdentityRepository userIdentityRepository;

    public boolean isAdmin(UUID nodeId) {
        return userIdentityRepository.hasAdminEntitlement(nodeId);
    }

}
