package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.MeshNodeConsent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class MeshNodeConsentRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    MeshNodeConsentRepository repository;

    @Test
    void hasActiveConsent_trueWhenRowExists() {
        @SuppressWarnings("unchecked")
        TypedQuery<UUID> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(UUID.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(UUID.randomUUID()));

        assertTrue(repository.hasActiveConsent(UUID.randomUUID(), "scope"));
    }

    @Test
    void findActiveScopes_and_findActiveByNodeId() {
        @SuppressWarnings("unchecked")
        TypedQuery<String> scopeQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(String.class))).thenReturn(scopeQuery);
        when(scopeQuery.setParameter(anyString(), any())).thenReturn(scopeQuery);
        when(scopeQuery.getResultList()).thenReturn(List.of("a", "b"));

        @SuppressWarnings("unchecked")
        TypedQuery<MeshNodeConsent> consentQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(MeshNodeConsent.class))).thenReturn(consentQuery);
        when(consentQuery.setParameter(anyString(), any())).thenReturn(consentQuery);
        when(consentQuery.getResultList()).thenReturn(List.of(new MeshNodeConsent()));

        assertEquals(2, repository.findActiveScopes(UUID.randomUUID()).size());
        assertEquals(1, repository.findActiveByNodeId(UUID.randomUUID()).size());
    }

    @Test
    void revokeMethods_executeUpdate() {
        Query query = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        UUID nodeId = UUID.randomUUID();
        assertEquals(1, repository.revokeByNodeAndScope(nodeId, "scope"));
        assertEquals(1, repository.revokeAllForNode(nodeId));
        assertEquals(1, repository.markAllRevoked(nodeId, Instant.now()));
    }

    @Test
    void persist_delegatesToEntityManager() {
        MeshNodeConsent consent = new MeshNodeConsent();
        repository.persist(consent);
        verify(em).persist(consent);
    }
}
