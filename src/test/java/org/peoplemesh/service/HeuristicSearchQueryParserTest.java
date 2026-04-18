package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.ParsedSearchQuery;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicSearchQueryParserTest {

    private final HeuristicSearchQueryParser parser = new HeuristicSearchQueryParser();

    @Test
    void parse_blankQuery_returnsEmpty() {
        assertTrue(parser.parse("   ").isEmpty());
    }

    @Test
    void parse_detectsRoleAndLanguageAndSkills() {
        Optional<ParsedSearchQuery> result = parser.parse("looking for senior java developer italian");
        assertTrue(result.isPresent());
        ParsedSearchQuery parsed = result.get();

        assertTrue(parsed.mustHave().roles().contains("developer"));
        assertTrue(parsed.mustHave().languages().contains("Italian"));
        assertTrue(parsed.mustHave().skills().contains("java"));
        assertFalse(parsed.mustHave().skills().contains("developer"));
        assertEquals("people", parsed.resultScope());
    }

    @Test
    void parse_keepsContextKeywordsAsKeywords() {
        Optional<ParsedSearchQuery> result = parser.parse("java meetup communities");
        assertTrue(result.isPresent());
        assertTrue(result.get().keywords().contains("meetup"));
        assertTrue(result.get().keywords().contains("communities"));
        assertEquals("communities", result.get().resultScope());
    }

    @Test
    void parse_setsDefaultSeniority() {
        Optional<ParsedSearchQuery> result = parser.parse("python engineer");
        assertTrue(result.isPresent());
        assertEquals("unknown", result.get().seniority());
    }

    @Test
    void parse_allSignal_returnsScopeAll() {
        Optional<ParsedSearchQuery> result = parser.parse("tutti i risultati con Java e Kubernetes");
        assertTrue(result.isPresent());
        assertEquals("all", result.get().resultScope());
    }

    @Test
    void parse_communityKeyword_returnsScopeCommunities() {
        Optional<ParsedSearchQuery> result = parser.parse("community data engineering in europe");
        assertTrue(result.isPresent());
        assertEquals("communities", result.get().resultScope());
    }

    @Test
    void parse_skillQueryNoEntity_returnsScopePeople() {
        Optional<ParsedSearchQuery> result = parser.parse("java architect");
        assertTrue(result.isPresent());
        assertEquals("people", result.get().resultScope());
    }
}
