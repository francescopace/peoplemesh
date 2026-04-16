package org.peoplemesh.domain.dto;

import java.util.List;
import java.util.Map;

public record AtsIngestResultDto(
        int upserted,
        int failed,
        List<Map<String, String>> errors
) {
}
