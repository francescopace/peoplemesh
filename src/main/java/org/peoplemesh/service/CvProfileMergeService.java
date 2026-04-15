package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CvProfileMergeService {

    public static final String SOURCE_CV = "cv_docling_llm";
    public static final String SOURCE_GITHUB = "github";

    public static String sourceForProvider(String provider) {
        return switch (provider) {
            case "github" -> SOURCE_GITHUB;
            default -> provider;
        };
    }
}
