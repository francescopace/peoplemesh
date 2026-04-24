package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.repository.MeshNodeSearchRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceCoverageTest {

    @Mock
    MeshNodeSearchRepository searchRepository;

    @Mock
    AppConfig config;

    @Mock
    ConsentService consentService;

    @Mock
    SemanticSkillMatcher semanticSkillMatcher;

    @InjectMocks
    MatchingService service;

    @Test
    void findAllMatches_withoutConsent_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(false);

        List<MeshMatchResult> result = service.findAllMatches(userId, new float[]{0.1f}, null, null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(searchRepository);
    }
}
