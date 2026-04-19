package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.repository.MeshNodeSearchRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingServiceCoverageTest {

    @Mock
    private MeshNodeSearchRepository searchRepository;
    @Mock
    private AppConfig config;
    @Mock
    private AppConfig.MatchingConfig matchingConfig;
    @Mock
    private AppConfig.SearchConfig searchConfig;
    @Mock
    private ConsentService consentService;
    @Mock
    private SkillLevelResolutionService skillLevelResolutionService;
    @Mock
    private SemanticSkillMatcher semanticSkillMatcher;

    private MatchingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MatchingService();
        when(config.matching()).thenReturn(matchingConfig);
        when(config.search()).thenReturn(searchConfig);
        when(matchingConfig.candidatePoolSize()).thenReturn(20);
        when(matchingConfig.resultLimit()).thenReturn(20);
        when(matchingConfig.decayLambda()).thenReturn(0.1);
        when(searchConfig.skillMatchThreshold()).thenReturn(0.7);

        setField("searchRepository", searchRepository);
        setField("config", config);
        setField("consentService", consentService);
        setField("skillLevelResolutionService", skillLevelResolutionService);
        setField("semanticSkillMatcher", semanticSkillMatcher);
    }

    @Test
    void findAllMatches_withoutConsent_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(false);

        List<MeshMatchResult> out = service.findAllMatches(userId, new float[]{0.1f}, null, null);

        assertTrue(out.isEmpty());
        verifyNoInteractions(searchRepository);
    }

    @Test
    void findAllMatches_nullEmbedding_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);

        List<MeshMatchResult> out = service.findAllMatches(userId, null, null, null);

        assertTrue(out.isEmpty());
        verifyNoInteractions(searchRepository);
    }

    @Test
    void doFindAllMatches_buildsPeopleAndNodeResults() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID peopleNodeId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Object[] nodeRow1 = nodeCandidateRow(nodeId, "IT", 0.62);
        Object[] nodeRow2 = nodeCandidateRow(UUID.randomUUID(), "US", 0.99);
        List<Object[]> peopleRows = new ArrayList<>();
        peopleRows.add(userCandidateRow(peopleNodeId));
        List<Object[]> nodeRows = new ArrayList<>();
        nodeRows.add(nodeRow1);
        nodeRows.add(nodeRow2);

        when(searchRepository.findUserCandidatesByEmbedding(any(), eq(userId), eq(20))).thenReturn(peopleRows);
        when(searchRepository.findNodeCandidatesByEmbedding(any(), eq(userId), eq(null), eq(20))).thenReturn(nodeRows);
        when(semanticSkillMatcher.matchSkills(any(), any(), eq(0.7)))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Java", "java", 0.99)));
        when(skillLevelResolutionService.resolveForNodeIds(List.of(peopleNodeId)))
                .thenReturn(new SkillLevelResolutionService.SkillLevels(
                        Map.of(),
                        Map.of(peopleNodeId, Map.of("java", (short) 4))
                ));

        MatchFilters filters = new MatchFilters(
                List.of("Java"),
                WorkMode.REMOTE,
                EmploymentType.OPEN_TO_OFFERS,
                "IT",
                List.of(new SkillWithLevel("Java", 3))
        );

        List<MeshMatchResult> out = invokeDoFindAllMatches(
                userId,
                new float[]{0.3f, 0.9f},
                List.of("Java", "Kubernetes"),
                List.of("java", "backend"),
                "IT",
                WorkMode.REMOTE,
                EmploymentType.FREELANCE,
                filters,
                null,
                "IT",
                10,
                0
        );

        assertEquals(2, out.size());
        assertEquals("PEOPLE", out.get(0).nodeType());
        assertNotNull(out.get(0).person());
        assertEquals("Alice Profile", out.get(0).title());
        assertNotNull(out.get(0).breakdown());
        assertTrue(out.get(0).breakdown().commonItems().contains("Java"));
        assertEquals("JOB", out.get(1).nodeType());
        assertNull(out.get(1).person());
        assertEquals("Senior Java Engineer", out.get(1).title());
        verify(semanticSkillMatcher).matchSkills(any(), any(), eq(0.7));
    }

    @Test
    void doFindAllMatches_invalidNodeType_returnsEmpty() throws Exception {
        List<MeshMatchResult> out = invokeDoFindAllMatches(
                UUID.randomUUID(),
                new float[]{0.2f},
                List.of(),
                List.of(),
                null,
                null,
                null,
                new MatchFilters(null, null, null, null),
                "not-a-type",
                null,
                10,
                0
        );

        assertTrue(out.isEmpty());
        verifyNoInteractions(searchRepository);
    }

    private List<MeshMatchResult> invokeDoFindAllMatches(
            UUID excludeUserId,
            float[] embedding,
            List<String> referenceTags,
            List<String> nodeReferenceTags,
            String referenceCountry,
            WorkMode referenceWorkMode,
            EmploymentType referenceEmploymentType,
            MatchFilters peopleFilters,
            String typeFilter,
            String countryFilter,
            Integer limit,
            Integer offset
    ) throws Exception {
        Method m = MatchingService.class.getDeclaredMethod(
                "doFindAllMatches",
                UUID.class,
                float[].class,
                List.class,
                List.class,
                String.class,
                WorkMode.class,
                EmploymentType.class,
                MatchFilters.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class
        );
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MeshMatchResult> results = (List<MeshMatchResult>) m.invoke(
                service,
                excludeUserId,
                embedding,
                referenceTags,
                nodeReferenceTags,
                referenceCountry,
                referenceWorkMode,
                referenceEmploymentType,
                peopleFilters,
                typeFilter,
                countryFilter,
                limit,
                offset
        );
        return results;
    }

    private Object[] userCandidateRow(UUID nodeId) {
        return new Object[]{
                nodeId,
                UUID.randomUUID(),
                "MID",
                new String[]{"Java", "Kotlin"},
                new String[]{"communication"},
                new String[]{"Docker"},
                "REMOTE",
                "OPEN_TO_OFFERS",
                new String[]{"backend"},
                new String[]{"mentoring"},
                "IT",
                "Europe/Rome",
                Instant.now().minusSeconds(86400L * 3),
                "Milan",
                0.91,
                "Alice Profile",
                "Engineer,Lead",
                new String[]{"hiking"},
                new String[]{"cycling"},
                new String[]{"climate"},
                "https://avatar.example",
                "@alice",
                "alice@example.com",
                "@alice_telegram",
                "+39-555",
                "https://linkedin.com/in/alice"
        };
    }

    private Object[] nodeCandidateRow(UUID nodeId, String country, double score) {
        return new Object[]{
                nodeId,
                "JOB",
                "Senior Java Engineer",
                "Build distributed systems",
                new String[]{"java", "backend"},
                country,
                Instant.now().minusSeconds(86400L * 2),
                score
        };
    }

    private void setField(String name, Object value) throws Exception {
        Field f = MatchingService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
