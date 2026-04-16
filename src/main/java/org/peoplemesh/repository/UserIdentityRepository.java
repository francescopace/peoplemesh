package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.model.UserIdentity;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserIdentityRepository {

    public Optional<UserIdentity> findByOauthSubject(String oauthSubject) {
        return UserIdentity.find("oauthSubject = ?1", oauthSubject).firstResultOptional();
    }

    public Optional<UserIdentity> findByProviderAndSubject(String provider, String subject) {
        return UserIdentity.findByOauth(provider, subject);
    }

    public void persist(UserIdentity identity) {
        identity.persist();
    }

    public Optional<UserIdentity> findById(UUID id) {
        return UserIdentity.findByIdOptional(id);
    }
}
