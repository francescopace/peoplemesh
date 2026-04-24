package org.peoplemesh.domain.dto;

import java.util.List;

public record SearchResponse(
        SearchQuery parsedQuery,
        List<SearchResultItem> results
) {}
