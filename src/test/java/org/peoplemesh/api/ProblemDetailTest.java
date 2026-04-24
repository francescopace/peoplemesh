package org.peoplemesh.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.peoplemesh.api.error.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProblemDetailTest {

    @Test
    void constructor_exposesAllComponents() {
        ProblemDetail p = new ProblemDetail("https://example.com/p/1", "Not found", 404, "Missing", "/req/9");
        assertEquals("https://example.com/p/1", p.type());
        assertEquals("Not found", p.title());
        assertEquals(404, p.status());
        assertEquals("Missing", p.detail());
        assertEquals("/req/9", p.instance());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 429, 500, 599})
    void of_withoutConfiguredBaseUri_usesAboutBlankAsTypeForAllStatuses(int status) {
        ProblemDetail p = ProblemDetail.of(status, "T", "D");
        assertEquals("about:blank", p.type());
        assertEquals("T", p.title());
        assertEquals(status, p.status());
        assertEquals("D", p.detail());
        assertNull(p.instance());
    }
}
