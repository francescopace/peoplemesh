package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CurrentUserService {

    @Inject
    SecurityIdentity identity;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    public Optional<UUID> findCurrentUserId() {
        UUID sessionUserId = identity.<UUID>getAttribute("pm.userId");
        if (sessionUserId != null) {
            return nodeRepository.findById(sessionUserId).map(node -> sessionUserId);
        }
        return findOauthLinkedUserId();
    }

    public UUID resolveUserId() {
        return findCurrentUserId().orElseThrow(this::missingCurrentUserException);
    }

    private Optional<UUID> findOauthLinkedUserId() {
        if (identity.isAnonymous()) {
            return Optional.empty();
        }
        String subject = identity.getPrincipal().getName();
        return userIdentityRepository.findByOauthSubject(subject).map(u -> u.nodeId);
    }

    private RuntimeException missingCurrentUserException() {
        if (identity.<UUID>getAttribute("pm.userId") != null) {
            return new BusinessException(401, "Unauthorized", "Session expired. Please log in again.");
        }
        return new SecurityException("User not registered. Please log in via /api/v1/auth/login/{provider} first.");
    }
}
