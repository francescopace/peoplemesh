package org.peoplemesh.service;

import org.peoplemesh.domain.model.MeshNode;

import java.util.UUID;

public interface NodeAccessPolicyService {
    boolean canReadNode(UUID requesterUserId, MeshNode node);
}
