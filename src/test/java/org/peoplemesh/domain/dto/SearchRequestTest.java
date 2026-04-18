package org.peoplemesh.domain.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTest {

    @Test
    void recordAccessors() {
        SearchRequest req = new SearchRequest("java developer");
        assertEquals("java developer", req.query());
    }
}
