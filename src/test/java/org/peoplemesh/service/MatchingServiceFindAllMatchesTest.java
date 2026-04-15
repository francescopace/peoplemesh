package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.MeshNodeSearchRepository;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingServiceFindAllMatchesTest {

    private final MatchingService service = new MatchingService();
    private MeshNodeSearchRepository searchRepository;
    private ConsentService consentService;

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.MatchingConfig matching = mock(AppConfig.MatchingConfig.class);
        when(config.matching()).thenReturn(matching);
        when(matching.decayLambda()).thenReturn(0.1);
        when(matching.candidatePoolSize()).thenReturn(50);
        when(matching.resultLimit()).thenReturn(20);

        searchRepository = mock(MeshNodeSearchRepository.class);
        consentService = mock(ConsentService.class);

        setField("config", config);
        setField("searchRepository", searchRepository);
        setField("consentService", consentService);
    }

    @Test
    void findAllMatches_withoutConsent_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(false);

        List<MeshMatchResult> out = service.findAllMatches(userId, "PEOPLE", null);

        assertTrue(out.isEmpty());
        verify(searchRepository, never()).findUserCandidatesByEmbedding(any(), any(), anyInt());
    }

    @Test
    void findAllMatches_profileMissingOrWithoutEmbedding_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        MeshNode noEmbedding = new MeshNode();
        noEmbedding.id = userId;
        noEmbedding.nodeType = NodeType.USER;
        noEmbedding.embedding = null;

        try (var meshMock = mockStatic(MeshNode.class)) {
            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.empty());
            assertTrue(service.findAllMatches(userId, null, null).isEmpty());

            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.of(noEmbedding));
            assertTrue(service.findAllMatches(userId, null, null).isEmpty());
        }
    }

    @Test
    void findAllMatches_peopleAndNodes_mergesAndSortsResults() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);

        MeshNode me = new MeshNode();
        me.id = userId;
        me.nodeType = NodeType.USER;
        me.embedding = new float[]{0.2f, 0.3f};
        me.tags = List.of("java");
        me.country = "IT";
        me.structuredData = new LinkedHashMap<>();
        me.structuredData.put("work_mode", "HYBRID");

        Object[] peopleRow = new Object[]{
                UUID.randomUUID(), UUID.randomUUID(), "MID",
                List.of("Java"), List.of(), List.of("Quarkus"),
                "HYBRID", "EMPLOYED", List.of(), List.of(),
                "IT", "Europe/Rome", Instant.now(), "Rome", 0.9d,
                "Jane", "Engineer,Developer", List.of(), List.of(), List.of(),
                "https://img", "@jane", "jane@example.com"
        };

        Object[] nodeRow = new Object[]{
                UUID.randomUUID(), "PROJECT", "Platform revamp", "desc",
                List.of("java", "platform"), "IT", Instant.now(), 0.7d
        };

        when(searchRepository.findUserCandidatesByEmbedding(any(float[].class), eq(userId), eq(50)))
                .thenReturn(java.util.Collections.singletonList(peopleRow));
        when(searchRepository.findNodeCandidatesByEmbedding(any(float[].class), eq(userId), eq(null), eq(50)))
                .thenReturn(java.util.Collections.singletonList(nodeRow));

        try (var meshMock = mockStatic(MeshNode.class)) {
            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.of(me));

            List<MeshMatchResult> out = service.findAllMatches(userId, null, null);

            assertEquals(2, out.size());
            assertEquals("PEOPLE", out.get(0).nodeType());
            assertEquals("PROJECT", out.get(1).nodeType());
        }
    }

    @Test
    void findAllMatches_invalidNodeTypeFilter_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);

        List<MeshMatchResult> out = service.findAllMatches(userId, new float[]{0.1f}, "NOT_A_TYPE", null);

        assertTrue(out.isEmpty());
        verify(searchRepository, never()).findNodeCandidatesByEmbedding(any(float[].class), any(), any(), anyInt());
    }

    private void setField(String name, Object value) throws Exception {
        Field f = MatchingService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
