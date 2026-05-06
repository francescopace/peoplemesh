package org.peoplemesh.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(EmbeddingServiceOpenAiModelTest.OpenAiEmbeddingModelTestProfile.class)
class EmbeddingServiceOpenAiModelTest {

    @Inject
    EmbeddingService embeddingService;

    @Test
    void resolveEmbeddingModel_usesConfiguredOpenAiDimension() {
        EmbeddingModel embeddingModel = embeddingService.resolveEmbeddingModel();

        assertEquals("text-embedding-3-small", embeddingModel.modelName());
        assertEquals(384, embeddingModel.dimension());
    }

    public static class OpenAiEmbeddingModelTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.langchain4j.openai.api-key", "test-key",
                    "quarkus.langchain4j.openai.embedding-model.enabled", "true",
                    "quarkus.langchain4j.openai.embedding-model.model-name", "text-embedding-3-small",
                    "peoplemesh.embedding.dimension", "384"
            );
        }
    }
}
