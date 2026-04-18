package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.peoplemesh.repository.NodeRepository;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingServiceFindAllMatchesTest {

    private final MatchingService service = new MatchingService();
    private MeshNodeSearchRepository searchRepository;
    private ConsentService consentService;
    private NodeRepository nodeRepository;
    private SemanticSkillMatcher semanticSkillMatcher;

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.MatchingConfig matching = mock(AppConfig.MatchingConfig.class);
        AppConfig.SearchConfig search = mock(AppConfig.SearchConfig.class);
        when(config.matching()).thenReturn(matching);
        when(config.search()).thenReturn(search);
        when(matching.decayLambda()).thenReturn(0.1);
        when(matching.candidatePoolSize()).thenReturn(50);
        when(matching.resultLimit()).thenReturn(20);
        when(search.skillMatchThreshold()).thenReturn(0.7);

        searchRepository = mock(MeshNodeSearchRepository.class);
        consentService = mock(ConsentService.class);
        nodeRepository = mock(NodeRepository.class);
        semanticSkillMatcher = mock(SemanticSkillMatcher.class);
        when(semanticSkillMatcher.matchSkills(any(), any(), anyDouble())).thenAnswer(invocation -> {
            List<String> querySkills = invocation.getArgument(0);
            List<String> candidateSkills = invocation.getArgument(1);
            return querySkills.stream()
                    .filter(q -> candidateSkills.stream().anyMatch(c -> MatchingUtils.termsMatch(q, c)))
                    .map(q -> new SemanticSkillMatcher.SemanticMatch(q, q, 1.0))
                    .toList();
        });

        setField("config", config);
        setField("searchRepository", searchRepository);
        setField("consentService", consentService);
        setField("nodeRepository", nodeRepository);
        setField("semanticSkillMatcher", semanticSkillMatcher);
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

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        assertTrue(service.findAllMatches(userId, null, null).isEmpty());

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(noEmbedding));
        assertTrue(service.findAllMatches(userId, null, null).isEmpty());
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
                "https://img", "@jane", "jane@example.com", "@jane_tg", "+39123456789"
        };

        Object[] nodeRow = new Object[]{
                UUID.randomUUID(), "PROJECT", "Platform revamp", "desc",
                List.of("java", "platform"), "IT", Instant.now(), 0.7d
        };

        when(searchRepository.findUserCandidatesByEmbedding(any(float[].class), eq(userId), eq(50)))
                .thenReturn(java.util.Collections.singletonList(peopleRow));
        when(searchRepository.findNodeCandidatesByEmbedding(any(float[].class), eq(userId), eq(null), eq(50)))
                .thenReturn(java.util.Collections.singletonList(nodeRow));

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(me));

        List<MeshMatchResult> out = service.findAllMatches(userId, null, null);

        assertEquals(2, out.size());
        assertEquals("PEOPLE", out.get(0).nodeType());
        assertEquals("PROJECT", out.get(1).nodeType());
    }

    @Test
    void findAllMatches_withLimitAndOffset_returnsPagedSlice() {
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
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(me));

        Object[] peopleA = new Object[]{
                UUID.randomUUID(), UUID.randomUUID(), "MID",
                List.of("Java"), List.of(), List.of("Quarkus"),
                "HYBRID", "EMPLOYED", List.of(), List.of(),
                "IT", "Europe/Rome", Instant.now(), "Rome", 0.95d,
                "Alice", "Engineer", List.of(), List.of(), List.of(),
                "https://img", "@alice", "alice@example.com", "@alice_tg", "+39111111111"
        };
        Object[] peopleB = new Object[]{
                UUID.randomUUID(), UUID.randomUUID(), "MID",
                List.of("Java"), List.of(), List.of("Quarkus"),
                "HYBRID", "EMPLOYED", List.of(), List.of(),
                "IT", "Europe/Rome", Instant.now(), "Rome", 0.90d,
                "Bob", "Engineer", List.of(), List.of(), List.of(),
                "https://img", "@bob", "bob@example.com", "@bob_tg", "+39222222222"
        };
        Object[] peopleC = new Object[]{
                UUID.randomUUID(), UUID.randomUUID(), "MID",
                List.of("Java"), List.of(), List.of("Quarkus"),
                "HYBRID", "EMPLOYED", List.of(), List.of(),
                "IT", "Europe/Rome", Instant.now(), "Rome", 0.85d,
                "Carol", "Engineer", List.of(), List.of(), List.of(),
                "https://img", "@carol", "carol@example.com", "@carol_tg", "+39333333333"
        };

        when(searchRepository.findUserCandidatesByEmbedding(any(float[].class), eq(userId), eq(50)))
                .thenReturn(List.of(peopleA, peopleB, peopleC));
        when(searchRepository.findNodeCandidatesByEmbedding(any(float[].class), eq(userId), eq(null), eq(50)))
                .thenReturn(List.of());

        List<MeshMatchResult> out = service.findAllMatches(userId, "PEOPLE", null, 1, 1);

        assertEquals(1, out.size());
        assertEquals("Bob", out.get(0).title());
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
