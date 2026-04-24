package org.peoplemesh.service;

import org.peoplemesh.domain.dto.SearchMatchBreakdown;

record ScoredCandidate(
        RawNodeCandidate node,
        SearchMatchBreakdown breakdown,
        double score
) {
}
