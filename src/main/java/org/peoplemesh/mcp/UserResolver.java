package org.peoplemesh.mcp;

import io.quarkus.security.identity.SecurityIdentity;
import org.peoplemesh.domain.model.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Resolves the authenticated MCP/API caller to an internal user ID.
 */
@ApplicationScoped
public class UserResolver {

    @Inject
    SecurityIdentity identity;

    public UUID resolveUserId() {
        String subject = identity.getPrincipal().getName();
        return UserIdentity.find("oauthSubject = ?1 and deletedAt is null", subject)
                .<UserIdentity>firstResultOptional()
                .map(u -> u.id)
                .orElseThrow(() -> new SecurityException(
                        "User not registered. Call /api/auth/register first."));
    }
}
