package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.WorkMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeographyUtilsTest {

    @Test
    void geographyScore_sameCountry_returnsOne() {
        assertEquals(1.0, GeographyUtils.geographyScore("IT", "IT", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_crossCountry_returnsZero() {
        assertEquals(0.0, GeographyUtils.geographyScore("IT", "DE", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyScore_differentContinent_returnsZero() {
        assertEquals(0.0, GeographyUtils.geographyScore("IT", "US", WorkMode.HYBRID), 0.001);
    }

    @Test
    void geographyReason_differentRegion() {
        assertEquals("different_region", GeographyUtils.geographyReason("US", "JP", WorkMode.HYBRID));
    }

    @Test
    void mapLocationToCountryCode_isoCodeAndAliases() {
        assertEquals("IT", GeographyUtils.mapLocationToCountryCode("it"));
        assertNull(GeographyUtils.mapLocationToCountryCode("uk"));
        assertNull(GeographyUtils.mapLocationToCountryCode("england"));
    }

    @Test
    void mapLocationToCountryCode_ignoresRegionWords() {
        assertNull(GeographyUtils.mapLocationToCountryCode("europe"));
        assertNull(GeographyUtils.mapLocationToCountryCode("global"));
    }
}
