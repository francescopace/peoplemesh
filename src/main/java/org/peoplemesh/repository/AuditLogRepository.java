package org.peoplemesh.repository;

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
        String query = "FROM AuditLogEntry a WHERE a.userIdHash = :userHash ";
        if (suppressedActions != null && !suppressedActions.isEmpty()) {
            query += "AND a.action NOT IN :suppressedActions ";
        }
        query += "ORDER BY a.timestamp DESC";
        var typedQuery = em.createQuery(query, AuditLogEntry.class)
                .setParameter("userHash", userHash)
                .setMaxResults(pageSize);
        if (suppressedActions != null && !suppressedActions.isEmpty()) {
            typedQuery.setParameter("suppressedActions", suppressedActions);
        }
        return typedQuery.getResultList();
    }
}
