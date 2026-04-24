package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;

final class RepositoryPersistence {

    private RepositoryPersistence() {
    }

    static <T> void persistOrMerge(EntityManager entityManager, T entity, Object id) {
        if (id == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
    }
}
