package org.peoplemesh.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CvProfileMergeServiceTest {

    @Test
    void sourceForProvider_github_returnsConstant() {
        assertEquals(CvProfileMergeService.SOURCE_GITHUB, CvProfileMergeService.sourceForProvider("github"));
    }

    @Test
    void sourceForProvider_otherProvider_returnsUnchanged() {
        assertEquals("linkedin", CvProfileMergeService.sourceForProvider("linkedin"));
    }

    @Test
    void sourceForProvider_unknownProvider_returnsAsIs() {
        assertEquals("some-custom-provider", CvProfileMergeService.sourceForProvider("some-custom-provider"));
    }

    @Test
    void constants_haveExpectedValues() {
        assertEquals("cv_docling_llm", CvProfileMergeService.SOURCE_CV);
        assertEquals("github", CvProfileMergeService.SOURCE_GITHUB);
    }
}
