package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.UUID;

@ApplicationScoped
public class EntitlementService {

    @Inject
    UserIdentityRepository userIdentityRepository;

    public boolean canCreateJob(UUID nodeId) {
        return hasEntitlement(nodeId, "canCreateJob");
    }

    public boolean canManageSkills(UUID nodeId) {
        return hasEntitlement(nodeId, "canManageSkills");
    }

    boolean hasEntitlement(UUID nodeId, String entitlementField) {
        return userIdentityRepository.hasEntitlement(nodeId, entitlementField);
    }

}
