package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SearchQuery;

import java.util.Optional;

public interface SearchQueryParser {
    Optional<SearchQuery> parse(String userQuery);
}
