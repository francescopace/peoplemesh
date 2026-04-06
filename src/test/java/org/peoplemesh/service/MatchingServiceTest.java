package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.enums.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchingServiceTest {

    private final MatchingService service = new MatchingService();

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.MatchingConfig matchingConfig = mock(AppConfig.MatchingConfig.class);
        when(config.matching()).thenReturn(matchingConfig);
        when(matchingConfig.decayLambda()).thenReturn(0.1);
        when(matchingConfig.candidatePoolSize()).thenReturn(50);
        when(matchingConfig.resultLimit()).thenReturn(20);

        Field configField = MatchingService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);
    }

    @Test
    void jaccardSimilarity_identicalSets_returnsOne() {
        List<String> skills = List.of("Java", "Python", "SQL");
        assertEquals(1.0, service.jaccardSimilarity(skills, skills), 0.001);
    }

    @Test
    void jaccardSimilarity_disjointSets_returnsZero() {
        List<String> a = List.of("Java", "Python");
        List<String> b = List.of("Go", "Rust");
        assertEquals(0.0, service.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_partialOverlap() {
        List<String> a = List.of("Java", "Python", "SQL");
        List<String> b = List.of("Python", "SQL", "Go");
        assertEquals(0.5, service.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_caseInsensitive() {
        List<String> a = List.of("Java", "PYTHON");
        List<String> b = List.of("java", "python");
        assertEquals(1.0, service.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_nullOrEmpty_returnsZero() {
        assertEquals(0.0, service.jaccardSimilarity(null, List.of("a")), 0.001);
        assertEquals(0.0, service.jaccardSimilarity(List.of("a"), null), 0.001);
        assertEquals(0.0, service.jaccardSimilarity(Collections.emptyList(), List.of("a")), 0.001);
    }

    @Test
    void geographyScore_sameCountry_returnsOne() {
        assertEquals(1.0, service.geographyScore("IT", "IT", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_sameContinent_returnsHalf() {
        assertEquals(0.5, service.geographyScore("IT", "DE", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_differentContinent_returnsZero() {
        assertEquals(0.0, service.geographyScore("IT", "US", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_remoteMode_alwaysOne() {
        assertEquals(1.0, service.geographyScore("IT", "JP", WorkMode.REMOTE), 0.001);
    }

    @Test
    void geographyScore_nullCountry_returnsZero() {
        assertEquals(0.0, service.geographyScore(null, "IT", WorkMode.HYBRID), 0.001);
        assertEquals(0.0, service.geographyScore("IT", null, WorkMode.HYBRID), 0.001);
    }

    @Test
    void applyDecay_recentProfile_noDecay() {
        double score = 0.8;
        Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);
        assertEquals(score, service.applyDecay(score, recent), 0.001);
    }

    @Test
    void applyDecay_sixMonthsOld_noDecay() {
        double score = 0.8;
        Instant sixMonths = Instant.now().minus(180, ChronoUnit.DAYS);
        assertEquals(score, service.applyDecay(score, sixMonths), 0.001);
    }

    @Test
    void applyDecay_twelveMonthsOld_appliesExponentialDecay() {
        double score = 0.8;
        Instant old = Instant.now().minus(365, ChronoUnit.DAYS);
        double result = service.applyDecay(score, old);
        assertTrue(result < score, "Decayed score should be less than original");
        assertTrue(result > 0, "Decayed score should be positive");
    }
}
