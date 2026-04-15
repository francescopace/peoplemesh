package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.model.UserIdentity;

import java.util.UUID;

@ApplicationScoped
public class EntitlementService {

    public boolean canCreateJob(UUID nodeId) {
        return hasEntitlement(nodeId, "canCreateJob");
    }

    public boolean canManageSkills(UUID nodeId) {
        return hasEntitlement(nodeId, "canManageSkills");
    }

    boolean hasEntitlement(UUID nodeId, String entitlementField) {
        String query = "nodeId = ?1 and " + entitlementField + " = true";
        return UserIdentity.find(query, nodeId)
                .firstResultOptional().isPresent();
    }

}
