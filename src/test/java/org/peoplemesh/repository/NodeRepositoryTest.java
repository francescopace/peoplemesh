package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class NodeRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    NodeRepository repository;

    @Test
    void findById_returnsFirstResult() {
        TypedQuery<MeshNode> query = meshNodeQueryWithStream(List.of(new MeshNode()));
        when(em.createQuery(anyString(), eq(MeshNode.class))).thenReturn(query);

        Optional<MeshNode> result = repository.findById(UUID.randomUUID());

        assertTrue(result.isPresent());
    }

    @Test
    void findUserByExternalId_blank_returnsEmptyWithoutQuery() {
        Optional<MeshNode> result = repository.findUserByExternalId(" ");

        assertTrue(result.isEmpty());
    }

    @Test
    void findPublishedUserNode_and_findUserByExternalId_and_findJobBySourceAndExternalId() {
        MeshNode node = new MeshNode();
        TypedQuery<MeshNode> query = meshNodeQueryWithStream(List.of(node));
        when(em.createQuery(anyString(), eq(MeshNode.class))).thenReturn(query);

        assertTrue(repository.findPublishedUserNode(UUID.randomUUID()).isPresent());
        assertTrue(repository.findUserByExternalId("a@b.com").isPresent());
        assertTrue(repository.findJobBySourceAndExternalId("workday", "ext-1").isPresent());
    }

    @Test
    void findNodeIds_appliesFilters() {
        @SuppressWarnings("unchecked")
        TypedQuery<UUID> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(UUID.class))).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(UUID.randomUUID()));

        List<UUID> ids = repository.findNodeIds(NodeType.JOB, true);

        assertEquals(1, ids.size());
        verify(query).setParameter("nodeType", NodeType.JOB);
    }

    @Test
    void findByIds_emptyInput_returnsEmpty() {
        assertTrue(repository.findByIds(List.of()).isEmpty());
    }

    @Test
    void findByIds_nonEmpty_returnsRows() {
        TypedQuery<MeshNode> query = meshNodeQueryWithList(List.of(new MeshNode()));
        when(em.createQuery(anyString(), eq(MeshNode.class))).thenReturn(query);

        List<MeshNode> rows = repository.findByIds(List.of(UUID.randomUUID()));
        assertEquals(1, rows.size());
    }

    @Test
    void findUserIdsWithMissingEmbeddingByIdentityProvider_mapsRows() {
        Query query = mock(Query.class);
        UUID id = UUID.randomUUID();
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(id));

        List<UUID> ids = repository.findUserIdsWithMissingEmbeddingByIdentityProvider("google");
        assertEquals(List.of(id), ids);
    }

    @Test
    void listCommunities_and_countByType_work() {
        TypedQuery<MeshNode> nodeQuery = meshNodeQueryWithList(List.of(new MeshNode()));
        @SuppressWarnings("unchecked")
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(MeshNode.class))).thenReturn(nodeQuery);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertEquals(1, repository.listCommunities().size());
        assertEquals(1L, repository.countByType(NodeType.USER));
    }

    @Test
    void countByTypes_returnsValue() {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(3L);

        long count = repository.countByTypes(List.of(NodeType.USER, NodeType.JOB));
        assertEquals(3L, count);
    }

    @Test
    void persist_and_delete_followEntityManagerRules() {
        MeshNode newNode = new MeshNode();
        MeshNode existing = new MeshNode();
        existing.id = UUID.randomUUID();
        when(em.contains(existing)).thenReturn(false);
        when(em.merge(existing)).thenReturn(existing);

        repository.persist(newNode);
        repository.persist(existing);
        repository.delete(existing);
        repository.flush();

        verify(em).persist(newNode);
        verify(em, times(2)).merge(existing);
        verify(em).remove(existing);
        verify(em).flush();
    }

    private static TypedQuery<MeshNode> meshNodeQueryWithList(List<MeshNode> nodes) {
        @SuppressWarnings("unchecked")
        TypedQuery<MeshNode> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(nodes);
        return query;
    }

    private static TypedQuery<MeshNode> meshNodeQueryWithStream(List<MeshNode> nodes) {
        @SuppressWarnings("unchecked")
        TypedQuery<MeshNode> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenAnswer(invocation -> nodes.stream());
        return query;
    }
}
