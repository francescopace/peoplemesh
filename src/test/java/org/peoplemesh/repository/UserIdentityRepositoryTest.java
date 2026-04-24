package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.UserIdentity;

import java.util.List;
import java.util.Optional;
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
class UserIdentityRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    UserIdentityRepository repository;

    @Test
    void findByOauthSubject_returnsFirst() {
        @SuppressWarnings("unchecked")
        TypedQuery<UserIdentity> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(new UserIdentity()).stream());
        when(em.createQuery(anyString(), eq(UserIdentity.class))).thenReturn(query);

        Optional<UserIdentity> result = repository.findByOauthSubject("sub");
        assertTrue(result.isPresent());
    }

    @Test
    void findByNodeId_returnsList() {
        TypedQuery<UserIdentity> query = identityQueryWithList(List.of(new UserIdentity()));
        when(em.createQuery(anyString(), eq(UserIdentity.class))).thenReturn(query);

        assertEquals(1, repository.findByNodeId(UUID.randomUUID()).size());
    }

    @Test
    void hasAdminEntitlement_checksCount() {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        assertTrue(repository.hasAdminEntitlement(UUID.randomUUID()));
    }

    @Test
    void persist_andFindById() {
        UserIdentity newIdentity = new UserIdentity();
        UserIdentity existing = new UserIdentity();
        existing.id = UUID.randomUUID();
        when(em.find(UserIdentity.class, existing.id)).thenReturn(existing);

        repository.persist(newIdentity);
        repository.persist(existing);

        Optional<UserIdentity> found = repository.findById(existing.id);
        assertTrue(found.isPresent());
        verify(em).persist(newIdentity);
        verify(em).merge(existing);
    }

    private static TypedQuery<UserIdentity> identityQueryWithList(List<UserIdentity> rows) {
        @SuppressWarnings("unchecked")
        TypedQuery<UserIdentity> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }

}
