package org.peoplemesh.service;

import org.peoplemesh.domain.dto.ParsedSearchQuery;

import java.util.Optional;

public interface SearchQueryParser {
    Optional<ParsedSearchQuery> parse(String userQuery);
}
