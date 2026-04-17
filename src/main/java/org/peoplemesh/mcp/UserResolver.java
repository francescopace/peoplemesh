package org.peoplemesh.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.service.CurrentUserService;

import java.util.UUID;

/**
 * Resolves the authenticated MCP/API caller to a mesh_node ID.
 */
@ApplicationScoped
public class UserResolver {

    @Inject
    CurrentUserService currentUserService;

    public UUID resolveUserId() {
        return currentUserService.resolveUserId();
    }
}
