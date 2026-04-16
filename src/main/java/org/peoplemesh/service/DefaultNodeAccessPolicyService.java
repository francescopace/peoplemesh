package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.UUID;

@ApplicationScoped
public class DefaultNodeAccessPolicyService implements NodeAccessPolicyService {

    @Override
    public boolean canReadNode(UUID requesterUserId, MeshNode node) {
        if (requesterUserId == null || node == null) {
            return false;
        }
        boolean isOwner = requesterUserId.equals(node.createdBy);
        boolean isPublicNonUser = node.searchable && node.nodeType != NodeType.USER;
        return isOwner || isPublicNonUser;
    }
}
