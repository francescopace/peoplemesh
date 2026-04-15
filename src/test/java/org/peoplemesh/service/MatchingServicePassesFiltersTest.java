package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchingServicePassesFiltersTest {

    private final MatchingService service = new MatchingService();
    private Method passesFilters;

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

        passesFilters = MatchingService.class.getDeclaredMethod("passesFilters",
                MatchFilters.class, List.class, WorkMode.class, EmploymentType.class, String.class);
        passesFilters.setAccessible(true);
    }

    private boolean invoke(MatchFilters f, List<String> skills, WorkMode wm, EmploymentType et, String country)
            throws Exception {
        return (boolean) passesFilters.invoke(service, f, skills, wm, et, country);
    }

    @Test
    void noFilters_returnsTrue() throws Exception {
        assertTrue(invoke(new MatchFilters(null, null, null, null),
                List.of("Java"), WorkMode.REMOTE, EmploymentType.EMPLOYED, "IT"));
    }

    @Test
    void countryFilter_matchingCountry_returnsTrue() throws Exception {
        assertTrue(invoke(new MatchFilters(null, null, null, "IT"),
                List.of(), null, null, "IT"));
    }

    @Test
    void countryFilter_differentCountry_returnsFalse() throws Exception {
        assertFalse(invoke(new MatchFilters(null, null, null, "DE"),
                List.of(), null, null, "IT"));
    }

    @Test
    void countryFilter_nullCandidateCountry_returnsFalse() throws Exception {
        assertFalse(invoke(new MatchFilters(null, null, null, "IT"),
                List.of(), null, null, null));
    }

    @Test
    void workModeFilter_matching_returnsTrue() throws Exception {
        assertTrue(invoke(new MatchFilters(null, WorkMode.REMOTE, null, null),
                List.of(), WorkMode.REMOTE, null, null));
    }

    @Test
    void workModeFilter_different_returnsFalse() throws Exception {
        assertFalse(invoke(new MatchFilters(null, WorkMode.OFFICE, null, null),
                List.of(), WorkMode.REMOTE, null, null));
    }

    @Test
    void employmentTypeFilter_matching_returnsTrue() throws Exception {
        assertTrue(invoke(new MatchFilters(null, null, EmploymentType.FREELANCE, null),
                List.of(), null, EmploymentType.FREELANCE, null));
    }

    @Test
    void employmentTypeFilter_different_returnsFalse() throws Exception {
        assertFalse(invoke(new MatchFilters(null, null, EmploymentType.EMPLOYED, null),
                List.of(), null, EmploymentType.FREELANCE, null));
    }

    @Test
    void skillsFilter_hasOverlap_returnsTrue() throws Exception {
        assertTrue(invoke(new MatchFilters(List.of("Java"), null, null, null),
                List.of("Java", "Python"), null, null, null));
    }

    @Test
    void skillsFilter_noOverlap_returnsFalse() throws Exception {
        assertFalse(invoke(new MatchFilters(List.of("Go", "Rust"), null, null, null),
                List.of("Java", "Python"), null, null, null));
    }

    @Test
    void geographyScore_unknownCountries_returnsZeroForHybrid() {
        assertEquals(0.0, service.geographyScore("XX", "YY", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_sameCountryCaseInsensitive() {
        assertEquals(1.0, service.geographyScore("it", "IT", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_nullWorkMode_treatsAsNotRemote() {
        assertEquals(1.0, service.geographyScore("IT", "IT", null), 0.001);
        assertEquals(0.5, service.geographyScore("IT", "DE", null), 0.001);
    }

    @Test
    void applyDecay_nullUpdatedAt_returnsOriginal() {
        assertEquals(0.8, service.applyDecay(0.8, null), 0.001);
    }
}
