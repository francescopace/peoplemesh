package org.peoplemesh.domain.dto;

import java.util.List;

public record SearchResponse(
        ParsedSearchQuery parsedQuery,
        List<SearchResultItem> results,
        List<String> suggestions
) {}
