package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.UserIdentity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserIdentityRepository {

    @Inject
    EntityManager em;

    public Optional<UserIdentity> findByOauthSubject(String oauthSubject) {
        return em.createQuery("FROM UserIdentity u WHERE u.oauthSubject = :oauthSubject", UserIdentity.class)
                .setParameter("oauthSubject", oauthSubject)
                .getResultStream()
                .findFirst();
    }

    public Optional<UserIdentity> findByProviderAndSubject(String provider, String subject) {
        return em.createQuery(
                        "FROM UserIdentity u WHERE u.oauthProvider = :provider AND u.oauthSubject = :subject",
                        UserIdentity.class)
                .setParameter("provider", provider)
                .setParameter("subject", subject)
                .getResultStream()
                .findFirst();
    }

    public List<UserIdentity> findByNodeId(UUID nodeId) {
        return em.createQuery("FROM UserIdentity u WHERE u.nodeId = :nodeId", UserIdentity.class)
                .setParameter("nodeId", nodeId)
                .getResultList();
    }

    public boolean hasAdminEntitlement(UUID nodeId) {
        Long count = em.createQuery(
                        "SELECT COUNT(u) FROM UserIdentity u WHERE u.nodeId = :nodeId AND u.isAdmin = true",
                        Long.class)
                .setParameter("nodeId", nodeId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public void persist(UserIdentity identity) {
        if (identity.id == null) {
            em.persist(identity);
        } else {
            em.merge(identity);
        }
    }

    public Optional<UserIdentity> findById(UUID id) {
        return Optional.ofNullable(em.find(UserIdentity.class, id));
    }
}
