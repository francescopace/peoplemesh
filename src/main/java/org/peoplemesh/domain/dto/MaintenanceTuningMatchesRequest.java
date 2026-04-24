package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaintenanceTuningMatchesRequest(
        @JsonProperty("search_query") @NotNull @Valid SearchQuery searchQuery,
        @JsonProperty("search_options") @Valid SearchOptions searchOptions,
        @Size(max = 40) @Pattern(regexp = "^[A-Za-z_]*$") String type,
        @Pattern(regexp = "^[A-Za-z]{2}$|^$") String country,
        @Min(1) @Max(100) Integer limit,
        @Min(0) Integer offset
) {
    public boolean hasValidSearchOptions() {
        return searchOptions != null && searchOptions.hasOverrides();
    }
}
