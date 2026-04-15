package org.peoplemesh.mcp;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.NotAuthorizedException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Resolves the authenticated MCP/API caller to a mesh_node ID.
 */
@ApplicationScoped
public class UserResolver {

    @Inject
    SecurityIdentity identity;

    public UUID resolveUserId() {
        UUID sessionUserId = identity.<UUID>getAttribute("pm.userId");
        if (sessionUserId != null) {
            if (MeshNode.<MeshNode>findByIdOptional(sessionUserId).isEmpty()) {
                throw new NotAuthorizedException("Session expired. Please log in again.");
            }
            return sessionUserId;
        }

        String subject = identity.getPrincipal().getName();

        return UserIdentity.find("oauthSubject = ?1", subject)
                .<UserIdentity>firstResultOptional()
                .map(u -> u.nodeId)
                .orElseThrow(() -> new SecurityException(
                        "User not registered. Please log in via /api/v1/auth/login/{provider} first."));
    }
}
