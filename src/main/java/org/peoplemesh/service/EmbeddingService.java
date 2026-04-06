package org.peoplemesh.service;

import io.quarkus.arc.All;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.model.UserProfile;
import org.peoplemesh.matching.EmbeddingProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class EmbeddingService {

    @Inject
    @All
    List<EmbeddingProvider> providers;

    @Inject
    AppConfig config;

    @Inject
    EncryptionService encryption;

    public float[] generateEmbedding(UserProfile profile) {
        String text = profileToText(profile);
        return generateEmbedding(text);
    }

    public float[] generateEmbedding(String text) {
        return getActiveProvider().embed(text);
    }

    public int dimension() {
        return getActiveProvider().dimension();
    }

    private EmbeddingProvider getActiveProvider() {
        if (providers.isEmpty()) {
            throw new IllegalStateException("No EmbeddingProvider available for provider=" +
                    config.embedding().provider());
        }
        return providers.get(0);
    }

    /**
     * Builds a semantic text representation of the profile for embedding.
     * Encrypted fields are decrypted before inclusion so embeddings
     * reflect actual content, not ciphertext.
     */
    private String profileToText(UserProfile profile) {
        String roles = decryptCsv(profile.userId, profile.rolesEncrypted);
        String industries = decryptCsv(profile.userId, profile.industriesEncrypted);

        return Stream.of(
                        joinField("Roles", roles),
                        profile.seniority != null ? "Seniority: " + profile.seniority : null,
                        joinField("Industries", industries),
                        joinList("Technical Skills", profile.skillsTechnical),
                        joinList("Soft Skills", profile.skillsSoft),
                        joinList("Tools", profile.toolsAndTech),
                        joinList("Languages", profile.languagesSpoken),
                        profile.workMode != null ? "Work Mode: " + profile.workMode : null,
                        profile.employmentType != null ? "Employment: " + profile.employmentType : null,
                        joinList("Topics", profile.topicsFrequent),
                        joinList("Learning", profile.learningAreas),
                        joinList("Projects", profile.projectTypes),
                        profile.collaborationGoals != null
                                ? "Goals: " + profile.collaborationGoals.stream()
                                .map(Enum::name).collect(Collectors.joining(", "))
                                : null,
                        profile.country != null ? "Country: " + profile.country : null
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private String decryptCsv(java.util.UUID userId, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        return encryption.decrypt(userId, ciphertext);
    }

    private String joinList(String label, List<String> items) {
        if (items == null || items.isEmpty()) return null;
        return label + ": " + String.join(", ", items);
    }

    private String joinField(String label, String value) {
        if (value == null || value.isBlank()) return null;
        return label + ": " + value;
    }
}
