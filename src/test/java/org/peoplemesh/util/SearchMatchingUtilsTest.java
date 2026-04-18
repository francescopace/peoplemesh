package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchMatchingUtilsTest {

    @Test
    void deduplicateTerms_normalizesAndPreservesOrder() {
        List<String> result = SearchMatchingUtils.deduplicateTerms(
                List.of(" Java ", "java", "Node.js", "nodejs", "Kubernetes"));

        assertEquals(List.of("Java", "Node.js", "Kubernetes"), result);
    }

    @Test
    void paginate_returnsRequestedSlice() {
        List<Integer> paged = SearchMatchingUtils.paginate(List.of(1, 2, 3, 4, 5), 2, 1, 20);
        assertEquals(List.of(2, 3), paged);
    }

    @Test
    void paginate_usesDefaultLimitWhenNull() {
        List<Integer> paged = SearchMatchingUtils.paginate(List.of(1, 2, 3, 4), null, 1, 2);
        assertEquals(List.of(2, 3), paged);
    }
}
