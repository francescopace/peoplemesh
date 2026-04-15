package org.peoplemesh.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogCreateRequestTest {

    @Test
    void recordAccessors_returnConstructorValues() {
        Map<String, Object> scale = Map.of("1", "Beginner");
        CatalogCreateRequest req = new CatalogCreateRequest("Skills", "desc", scale, "internal");

        assertEquals("Skills", req.name());
        assertEquals("desc", req.description());
        assertEquals(scale, req.levelScale());
        assertEquals("internal", req.source());
    }

    @Test
    void recordAccessors_withNulls() {
        CatalogCreateRequest req = new CatalogCreateRequest("Name", null, null, null);

        assertEquals("Name", req.name());
        assertNull(req.description());
        assertNull(req.levelScale());
        assertNull(req.source());
    }
}
