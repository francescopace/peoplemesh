package org.peoplemesh.util;

import org.peoplemesh.domain.enums.WorkMode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeographyUtils {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final Map<String, String> COUNTRY_NAME_TO_ISO = buildCountryNameToIsoIndex();

    private GeographyUtils() {}

    public static double geographyScore(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myCountry == null || theirCountry == null) return 0.0;
        if (myCountry.equalsIgnoreCase(theirCountry)) return 1.0;
        return 0.0;
    }

    public static String geographyReason(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myCountry == null || theirCountry == null) return "location_unknown";
        if (myCountry.equalsIgnoreCase(theirCountry)) return "same_country";
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
        if (normalized.isBlank()) {
            return null;
        }
        return COUNTRY_NAME_TO_ISO.get(normalized);
    }

    private static Map<String, String> buildCountryNameToIsoIndex() {
        Map<String, String> index = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale locale = Locale.of("", code);
            index.put(code.toLowerCase(Locale.ROOT), code);
            index.put(locale.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
            index.put(locale.getDisplayCountry(Locale.ITALIAN).toLowerCase(Locale.ROOT), code);
        }
        return Map.copyOf(index);
    }
}
