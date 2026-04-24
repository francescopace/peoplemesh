package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.enums.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.util.MatchingUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchingServiceTest {

    private final MatchingService service = new MatchingService();

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.MatchingConfig matchingConfig = mock(AppConfig.MatchingConfig.class);
        when(config.matching()).thenReturn(matchingConfig);
        when(matchingConfig.candidatePoolSize()).thenReturn(50);

        Field configField = MatchingService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);
    }

    @Test
    void jaccardSimilarity_identicalSets_returnsOne() {
        List<String> skills = List.of("Java", "Python", "SQL");
        assertEquals(1.0, MatchingUtils.jaccardSimilarity(skills, skills), 0.001);
    }

    @Test
    void jaccardSimilarity_disjointSets_returnsZero() {
        List<String> a = List.of("Java", "Python");
        List<String> b = List.of("Go", "Rust");
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_partialOverlap() {
        List<String> a = List.of("Java", "Python", "SQL");
        List<String> b = List.of("Python", "SQL", "Go");
        assertEquals(0.5, MatchingUtils.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_caseInsensitive() {
        List<String> a = List.of("Java", "PYTHON");
        List<String> b = List.of("java", "python");
        assertEquals(1.0, MatchingUtils.jaccardSimilarity(a, b), 0.001);
    }

    @Test
    void jaccardSimilarity_nullOrEmpty_returnsZero() {
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(null, List.of("a")), 0.001);
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(List.of("a"), null), 0.001);
        assertEquals(0.0, MatchingUtils.jaccardSimilarity(Collections.emptyList(), List.of("a")), 0.001);
    }

    @Test
    void geographyScore_sameCountry_returnsOne() {
        assertEquals(1.0, service.geographyScore("IT", "IT", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_crossCountry_returnsZero() {
        assertEquals(0.0, service.geographyScore("IT", "DE", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_differentContinent_returnsZero() {
        assertEquals(0.0, service.geographyScore("IT", "US", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_remoteMode_hasNoGeoBoost() {
        assertEquals(0.0, service.geographyScore("IT", "JP", WorkMode.REMOTE), 0.001);
    }

    @Test
    void geographyScore_nullCountry_returnsZero() {
        assertEquals(0.0, service.geographyScore(null, "IT", WorkMode.HYBRID), 0.001);
        assertEquals(0.0, service.geographyScore("IT", null, WorkMode.HYBRID), 0.001);
    }
}
