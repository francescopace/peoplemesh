package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.util.GeographyUtils;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingServiceExtTest {

    private final MatchingService service = new MatchingService();

    @BeforeEach
    @SuppressWarnings("null")
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

        org.peoplemesh.repository.MeshNodeSearchRepository searchRepository =
                mock(org.peoplemesh.repository.MeshNodeSearchRepository.class);
        Field repoField = MatchingService.class.getDeclaredField("searchRepository");
        repoField.setAccessible(true);
        repoField.set(service, searchRepository);
    }

    @Test
    void passesFilters_noFilters_returnsTrue() throws Exception {
        MatchFilters filters = new MatchFilters(null, null, null, null);
        assertTrue(invokePassesFilters(filters, List.of("x"),
                WorkMode.REMOTE, EmploymentType.EMPLOYED, "US"));
    }

    @Test
    void passesFilters_countryMatch_returnsTrue() throws Exception {
        MatchFilters filters = new MatchFilters(null, null, null, "us");
        assertTrue(invokePassesFilters(filters, List.of(), null, null, "US"));
    }

    @Test
    void passesFilters_countryMismatch_returnsFalse() throws Exception {
        MatchFilters filters = new MatchFilters(null, null, null, "US");
        assertFalse(invokePassesFilters(filters, List.of(), null, null, "IT"));
    }

    @Test
    void passesFilters_workModeMatch_returnsTrue() throws Exception {
        MatchFilters filters = new MatchFilters(null, WorkMode.HYBRID, null, null);
        assertTrue(invokePassesFilters(filters, List.of(), WorkMode.HYBRID, null, null));
    }

    @Test
    void passesFilters_workModeMismatch_returnsFalse() throws Exception {
        MatchFilters filters = new MatchFilters(null, WorkMode.REMOTE, null, null);
        assertFalse(invokePassesFilters(filters, List.of(), WorkMode.HYBRID, null, null));
    }

    @Test
    void passesFilters_employmentTypeMatch_returnsTrue() throws Exception {
        MatchFilters filters = new MatchFilters(null, null, EmploymentType.EMPLOYED, null);
        assertTrue(invokePassesFilters(filters, List.of(), null, EmploymentType.EMPLOYED, null));
    }

    @Test
    void passesFilters_employmentTypeMismatch_returnsFalse() throws Exception {
        MatchFilters filters = new MatchFilters(null, null, EmploymentType.EMPLOYED, null);
        assertFalse(invokePassesFilters(filters, List.of(), null, EmploymentType.FREELANCE, null));
    }

    @Test
    void passesFilters_skillsOverlap_returnsTrue() throws Exception {
        MatchFilters filters = new MatchFilters(List.of("Java"), null, null, null);
        assertTrue(invokePassesFilters(filters, List.of("java", "Go"), null, null, null));
    }

    @Test
    void passesFilters_noSkillsOverlap_returnsFalse() throws Exception {
        MatchFilters filters = new MatchFilters(List.of("Java"), null, null, null);
        assertFalse(invokePassesFilters(filters, List.of("Go", "Rust"), null, null, null));
    }

    @Test
    void employmentCompatibility_nullCandidate_returnsHalf() throws Exception {
        assertEquals(0.5, invokeEmploymentCompatibility(null, EmploymentType.EMPLOYED));
    }

    @Test
    void employmentCompatibility_nullRequired_returnsHalf() throws Exception {
        assertEquals(0.5, invokeEmploymentCompatibility(EmploymentType.EMPLOYED, null));
    }

    @Test
    void employmentCompatibility_openToOffers_returnsOne() throws Exception {
        assertEquals(1.0, invokeEmploymentCompatibility(EmploymentType.OPEN_TO_OFFERS, EmploymentType.FREELANCE));
    }

    @Test
    void employmentCompatibility_looking_returnsOne() throws Exception {
        assertEquals(1.0, invokeEmploymentCompatibility(EmploymentType.LOOKING, EmploymentType.EMPLOYED));
    }

    @Test
    void employmentCompatibility_equal_returnsOne() throws Exception {
        assertEquals(1.0, invokeEmploymentCompatibility(EmploymentType.FREELANCE, EmploymentType.FREELANCE));
    }

    @Test
    void employmentCompatibility_mismatch_returnsZero() throws Exception {
        assertEquals(0.0, invokeEmploymentCompatibility(EmploymentType.EMPLOYED, EmploymentType.FREELANCE));
    }

    @Test
    void geographyReason_remote_returnsRemoteFriendly() {
        assertEquals("remote_friendly", GeographyUtils.geographyReason("US", "JP", WorkMode.REMOTE));
    }

    @Test
    void geographyReason_nullCountry_returnsLocationUnknown() {
        assertEquals("location_unknown", GeographyUtils.geographyReason(null, "US", WorkMode.HYBRID));
        assertEquals("location_unknown", GeographyUtils.geographyReason("US", null, WorkMode.HYBRID));
    }

    @Test
    void geographyReason_sameCountry_returnsSameCountry() {
        assertEquals("same_country", GeographyUtils.geographyReason("us", "US", WorkMode.HYBRID));
    }

    @Test
    void geographyReason_sameContinent_returnsSameContinent() {
        assertEquals("same_continent", GeographyUtils.geographyReason("US", "CA", WorkMode.HYBRID));
    }

    @Test
    void geographyReason_differentRegion_returnsDifferentRegion() {
        assertEquals("different_region", GeographyUtils.geographyReason("US", "JP", WorkMode.HYBRID));
    }

    @Test
    void sameContinent_knownPair_returnsTrue() {
        assertTrue(GeographyUtils.sameContinent("US", "CA"));
        assertTrue(GeographyUtils.sameContinent("de", "FR"));
    }

    @Test
    void sameContinent_differentContinents_returnsFalse() {
        assertFalse(GeographyUtils.sameContinent("US", "DE"));
    }

    @Test
    void sameContinent_unknownCountries_useAsymmetricDefaults_returnsFalse() {
        // CONTINENT_MAP misses use "XX" for a and "YY" for b, so unknown pairs never match.
        assertFalse(GeographyUtils.sameContinent("ZZ", "ZZ"));
        assertFalse(GeographyUtils.sameContinent("ZZ", "AA"));
    }

    @Test
    void buildReasonCodes_allSignals_present() throws Exception {
        List<String> codes = invokeBuildReasonCodes(
                0.7,
                List.of("Java"),
                0.5,
                0.9);
        assertEquals(
                List.of("SEMANTIC_SIMILARITY", "SKILLS_OVERLAP", "LOCATION_COMPATIBLE", "RECENCY_DECAY_APPLIED"),
                codes);
    }

    @Test
    void buildReasonCodes_minimalSignals_emptyLists() throws Exception {
        List<String> codes = invokeBuildReasonCodes(
                0.1,
                Collections.emptyList(),
                0.0,
                1.0);
        assertTrue(codes.isEmpty());
    }

    @Test
    void round3_roundsToThreeDecimals() throws Exception {
        assertEquals(1.235, invokeRound3(1.23456));
        assertEquals(1.234, invokeRound3(1.2344));
        assertEquals(-2.346, invokeRound3(-2.3456));
    }

    @Test
    void parseEnum_nullInput_returnsNull() throws Exception {
        assertNull(invokeParseEnum(WorkMode.class, null));
    }

    @Test
    void parseEnum_invalidName_returnsNull() throws Exception {
        assertNull(invokeParseEnum(WorkMode.class, "NOT_A_MODE"));
    }

    @Test
    void parseEnum_valid_returnsValue() throws Exception {
        assertEquals(WorkMode.REMOTE, invokeParseEnum(WorkMode.class, "REMOTE"));
        assertEquals(Seniority.MID, invokeParseEnum(Seniority.class, "MID"));
    }

    @Test
    void combineLists_nullAndValues_concatenates() throws Exception {
        assertEquals(List.of("a", "b"), invokeCombineLists(null, List.of("a", "b")));
        assertEquals(List.of("a", "b"), invokeCombineLists(List.of("a", "b"), null));
        assertEquals(List.of("a", "b", "c"), invokeCombineLists(List.of("a"), List.of("b", "c")));
    }

    @Test
    void intersectCaseInsensitive_overlapPreservesOrderFromFirst() throws Exception {
        List<String> out = invokeIntersectCaseInsensitive(
                List.of("Java", "Go", "java"),
                List.of("JAVA", "rust"));
        assertEquals(List.of("Java"), out);
    }

    @Test
    void intersectCaseInsensitive_nullOrEmpty_returnsEmpty() throws Exception {
        assertEquals(Collections.emptyList(), invokeIntersectCaseInsensitive(null, List.of("a")));
        assertEquals(Collections.emptyList(), invokeIntersectCaseInsensitive(List.of("a"), null));
        assertEquals(Collections.emptyList(), invokeIntersectCaseInsensitive(Collections.emptyList(), List.of("a")));
        assertEquals(Collections.emptyList(), invokeIntersectCaseInsensitive(List.of("a"), Collections.emptyList()));
    }

    @Test
    void intersectCaseInsensitive_skipsNullElements() throws Exception {
        List<String> a = new ArrayList<>();
        a.add("x");
        a.add(null);
        List<String> out = invokeIntersectCaseInsensitive(a, List.of("X"));
        assertEquals(List.of("x"), out);
    }

    @Test
    void parseArray_null_returnsEmptyList() throws Exception {
        List<String> result = SqlParsingUtils.parseArray(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseArray_stringArray_returnsList() throws Exception {
        List<String> result = SqlParsingUtils.parseArray(new String[]{"a", "b"});
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void parseArray_list_returnsList() throws Exception {
        List<String> input = List.of("x", "y");
        List<String> result = SqlParsingUtils.parseArray(input);
        assertEquals(input, result);
    }

    @Test
    void toInstant_null_returnsNull() throws Exception {
        Instant result = SqlParsingUtils.toInstant(null);
        assertNull(result);
    }

    @Test
    void toInstant_instant_returnsSame() throws Exception {
        Instant fixed = Instant.parse("2024-03-15T12:00:00Z");
        assertSame(fixed, SqlParsingUtils.toInstant(fixed));
    }

    @Test
    void toInstant_timestamp_convertsToInstant() throws Exception {
        Timestamp ts = Timestamp.from(Instant.parse("2023-07-01T08:30:00Z"));
        Instant result = SqlParsingUtils.toInstant(ts);
        assertEquals(ts.toInstant(), result);
    }

    @Test
    void toInstant_date_convertsToInstant() throws Exception {
        Date date = Date.from(Instant.parse("2022-11-20T00:00:00Z"));
        Instant result = SqlParsingUtils.toInstant(date);
        assertEquals(date.toInstant(), result);
    }

    @Test
    void toInstant_unsupportedType_throwsIAE() throws Exception {
        Exception cause = assertThrows(IllegalArgumentException.class, () -> SqlParsingUtils.toInstant("not-a-time"));
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(cause.getMessage().contains("Unsupported timestamp type"));
    }

    @Test
    void candidateRow_combinedSkillsAndTools_concatenatesSkillsAndTools() {
        MatchingService.CandidateRow row = new MatchingService.CandidateRow(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Seniority.MID,
                List.of("Java", "Rust"),
                List.of("soft-only"),
                List.of("Docker", "K8s"),
                WorkMode.REMOTE,
                EmploymentType.EMPLOYED,
                List.of(),
                List.of(),
                "US",
                "tz",
                Instant.EPOCH,
                null,
                0.0,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null);
        assertEquals(List.of("Java", "Rust", "Docker", "K8s"), row.combinedSkillsAndTools());
    }

    private boolean invokePassesFilters(
            MatchFilters filters,
            List<String> skills,
            WorkMode workMode,
            EmploymentType employmentType,
            String country) throws Exception {
        Method m = MatchingService.class.getDeclaredMethod(
                "passesFilters",
                MatchFilters.class,
                List.class,
                WorkMode.class,
                EmploymentType.class,
                String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, filters, skills, workMode, employmentType, country);
    }

    private double invokeEmploymentCompatibility(EmploymentType candidate, EmploymentType required) throws Exception {
        Method m = MatchingService.class.getDeclaredMethod(
                "employmentCompatibility", EmploymentType.class, EmploymentType.class);
        m.setAccessible(true);
        return (double) m.invoke(service, candidate, required);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeBuildReasonCodes(
            double cosineSim,
            List<String> matchedSkills,
            double geoScore,
            double decayMultiplier) throws Exception {
        Method m = MatchingService.class.getDeclaredMethod(
                "buildReasonCodes",
                double.class,
                List.class,
                double.class,
                double.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, cosineSim, matchedSkills, geoScore, decayMultiplier);
    }

    private double invokeRound3(double value) throws Exception {
        return StringUtils.round3(value);
    }

    private <E extends Enum<E>> E invokeParseEnum(Class<E> enumClass, String value) throws Exception {
        return SqlParsingUtils.parseEnum(enumClass, value);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeCombineLists(List<String> a, List<String> b) throws Exception {
        Method m = MatchingUtils.class.getDeclaredMethod("combineLists", List.class, List.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, a, b);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeIntersectCaseInsensitive(List<String> a, List<String> b) throws Exception {
        Method m = MatchingUtils.class.getDeclaredMethod("intersectCaseInsensitive", List.class, List.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, a, b);
    }

}
