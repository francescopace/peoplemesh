package org.peoplemesh.repository;

import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.AuditLogEntry;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class AuditLogRepository {

    @Inject
    EntityManager em;

    public void persist(AuditLogEntry entry) {
        em.persist(entry);
    }

    public List<AuditLogEntry> findByUserHash(String userIdHash, int limit) {
        return em.createQuery(
                        "FROM AuditLogEntry a WHERE a.userIdHash = :userIdHash ORDER BY a.timestamp DESC",
                        AuditLogEntry.class)
                .setParameter("userIdHash", userIdHash)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<AuditLogEntry> findRecentNotifications(String userHash, Set<String> suppressedActions, int pageSize) {
        return AuditLogEntry.find(
                        "userIdHash = ?1 and action not in ?2 order by timestamp desc",
                        userHash, suppressedActions)
                .page(Page.ofSize(pageSize))
                .list();
    }
}
