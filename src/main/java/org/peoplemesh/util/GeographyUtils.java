package org.peoplemesh.util;

import org.peoplemesh.domain.enums.WorkMode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeographyUtils {

    private static final Map<String, String> CONTINENT_MAP = Map.ofEntries(
            Map.entry("IT", "EU"), Map.entry("DE", "EU"), Map.entry("FR", "EU"),
            Map.entry("ES", "EU"), Map.entry("NL", "EU"), Map.entry("PT", "EU"),
            Map.entry("BE", "EU"), Map.entry("AT", "EU"), Map.entry("CH", "EU"),
            Map.entry("GB", "EU"), Map.entry("IE", "EU"), Map.entry("PL", "EU"),
            Map.entry("SE", "EU"), Map.entry("NO", "EU"), Map.entry("DK", "EU"),
            Map.entry("FI", "EU"), Map.entry("CZ", "EU"), Map.entry("RO", "EU"),
            Map.entry("US", "NA"), Map.entry("CA", "NA"), Map.entry("MX", "NA"),
            Map.entry("BR", "SA"), Map.entry("AR", "SA"), Map.entry("CL", "SA"),
            Map.entry("JP", "AS"), Map.entry("KR", "AS"), Map.entry("CN", "AS"),
            Map.entry("IN", "AS"), Map.entry("SG", "AS"), Map.entry("TH", "AS"),
            Map.entry("AU", "OC"), Map.entry("NZ", "OC")
    );

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final Map<String, String> COUNTRY_NAME_TO_ISO = buildCountryNameToIsoIndex();
    private static final Set<String> LOCATION_WORDS_TO_IGNORE = Set.of(
            "europe", "eu", "european union", "asia", "africa", "north america",
            "south america", "oceania", "worldwide", "global"
    );

    private GeographyUtils() {}

    public static double geographyScore(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return 1.0;
        if (myCountry == null || theirCountry == null) return 0.0;
        if (myCountry.equalsIgnoreCase(theirCountry)) return 1.0;
        if (sameContinent(myCountry, theirCountry)) return 0.5;
        return 0.0;
    }

    public static String geographyReason(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return "remote_friendly";
        if (myCountry == null || theirCountry == null) return "location_unknown";
        if (myCountry.equalsIgnoreCase(theirCountry)) return "same_country";
        if (sameContinent(myCountry, theirCountry)) return "same_continent";
        return "different_region";
    }

    public static String mapLocationToCountryCode(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return null;
        }
        String cleaned = rawLocation.trim();
        if (cleaned.length() == 2) {
            String cc = cleaned.toUpperCase(Locale.ROOT);
            if (ISO_COUNTRIES.contains(cc)) {
                return cc;
            }
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank() || LOCATION_WORDS_TO_IGNORE.contains(normalized)) {
            return null;
        }
        return COUNTRY_NAME_TO_ISO.get(normalized);
    }

    public static boolean sameContinent(String a, String b) {
        String contA = CONTINENT_MAP.getOrDefault(a.toUpperCase(Locale.ROOT), "XX");
        String contB = CONTINENT_MAP.getOrDefault(b.toUpperCase(Locale.ROOT), "YY");
        return contA.equals(contB);
    }

    private static Map<String, String> buildCountryNameToIsoIndex() {
        Map<String, String> index = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale locale = Locale.of("", code);
            index.put(code.toLowerCase(Locale.ROOT), code);
            index.put(locale.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
            index.put(locale.getDisplayCountry(Locale.ITALIAN).toLowerCase(Locale.ROOT), code);
        }
        index.put("uk", "GB");
        index.put("england", "GB");
        return Map.copyOf(index);
    }
}
