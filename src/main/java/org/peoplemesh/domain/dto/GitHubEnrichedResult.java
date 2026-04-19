package org.peoplemesh.domain.dto;

import java.util.List;

public record GitHubEnrichedResult(OidcSubject subject, List<String> languages, List<String> topics) {}
