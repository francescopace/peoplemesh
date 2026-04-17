package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.UUID;

@ApplicationScoped
public class CurrentUserService {

    @Inject
    SecurityIdentity identity;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    public UUID resolveUserId() {
        UUID sessionUserId = identity.<UUID>getAttribute("pm.userId");
        if (sessionUserId != null) {
            if (nodeRepository.findById(sessionUserId).isEmpty()) {
                throw new NotAuthorizedException("Session expired. Please log in again.");
            }
            return sessionUserId;
        }

        String subject = identity.getPrincipal().getName();
        return userIdentityRepository.findByOauthSubject(subject)
                .map(u -> u.nodeId)
                .orElseThrow(() -> new SecurityException(
                        "User not registered. Please log in via /api/v1/auth/login/{provider} first."));
    }
}
