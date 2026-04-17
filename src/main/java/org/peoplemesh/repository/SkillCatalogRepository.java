package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.SkillCatalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SkillCatalogRepository {

    @Inject
    EntityManager em;

    public void persist(SkillCatalog catalog) {
        if (catalog.id == null) {
            em.persist(catalog);
        } else {
            em.merge(catalog);
        }
    }

    public Optional<SkillCatalog> findById(UUID id) {
        return em.createQuery("FROM SkillCatalog c WHERE c.id = :id", SkillCatalog.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<SkillCatalog> findAllSorted() {
        return em.createQuery("FROM SkillCatalog c ORDER BY c.name", SkillCatalog.class)
                .getResultList();
    }

    public void delete(SkillCatalog catalog) {
        SkillCatalog managed = em.contains(catalog) ? catalog : em.merge(catalog);
        em.remove(managed);
    }
}
