package org.peoplemesh.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EmbeddingService {

    @ConfigProperty(name = "peoplemesh.embedding.dimension", defaultValue = "384")
    int targetVectorDimension = 384;

    @ConfigProperty(name = "quarkus.langchain4j.embedding-model.provider", defaultValue = "ollama")
    String embeddingProvider = "ollama";

    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    Optional<String> openAiApiKey;

    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url", defaultValue = "https://api.openai.com/v1")
    String openAiBaseUrl;

    @ConfigProperty(name = "quarkus.langchain4j.openai.organization-id")
    Optional<String> openAiOrganizationId;

    @ConfigProperty(name = "quarkus.langchain4j.openai.timeout")
    Optional<Duration> openAiTimeout;

    @ConfigProperty(name = "quarkus.langchain4j.openai.max-retries", defaultValue = "2")
    int openAiMaxRetries = 2;

    @ConfigProperty(name = "quarkus.langchain4j.openai.embedding-model.model-name", defaultValue = "text-embedding-3-small")
    String openAiModelName = "text-embedding-3-small";

    @ConfigProperty(name = "quarkus.langchain4j.openai.embedding-model.log-requests")
    Optional<Boolean> openAiLogRequests;

    @ConfigProperty(name = "quarkus.langchain4j.openai.embedding-model.log-responses")
    Optional<Boolean> openAiLogResponses;

    @ConfigProperty(name = "quarkus.langchain4j.openai.embedding-model.user")
    Optional<String> openAiUser;

    @Inject
    Instance<EmbeddingModel> embeddingModelInstance;

    private volatile EmbeddingModel resolvedEmbeddingModel;

    @Timed(
            value = "peoplemesh.embedding.inference",
            description = "Embedding inference latency",
            percentiles = {0.95},
            histogram = true
    )
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Embedding embedding = resolveEmbeddingModel().embed(text).content();
        return embedding == null ? null : validateVectorDimensions(embedding.vector());
    }

    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> validIndexes = new ArrayList<>(texts.size());
        List<TextSegment> segments = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            result.add(null);
            if (text == null || text.isBlank()) {
                continue;
            }
            validIndexes.add(i);
            segments.add(TextSegment.from(text));
        }

        if (segments.isEmpty()) {
            return result;
        }

        Response<List<Embedding>> response = resolveEmbeddingModel().embedAll(segments);
        List<Embedding> embeddings = response.content();
        if (embeddings == null || embeddings.size() != validIndexes.size()) {
            throw new IllegalStateException("Embedding batch response size mismatch");
        }
        for (int i = 0; i < validIndexes.size(); i++) {
            result.set(validIndexes.get(i), validateVectorDimensions(embeddings.get(i).vector()));
        }
        return result;
    }

    private float[] validateVectorDimensions(float[] vector) {
        if (vector == null) {
            return null;
        }
        if (vector.length == targetVectorDimension) {
            return vector;
        }
        throw new IllegalStateException(
                "Embedding dimension mismatch: expected "
                        + targetVectorDimension
                        + ", got "
                        + vector.length
        );
    }

    EmbeddingModel resolveEmbeddingModel() {
        EmbeddingModel current = resolvedEmbeddingModel;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (resolvedEmbeddingModel == null) {
                resolvedEmbeddingModel = createEmbeddingModel();
            }
            return resolvedEmbeddingModel;
        }
    }

    private EmbeddingModel createEmbeddingModel() {
        if ("openai".equalsIgnoreCase(embeddingProvider)) {
            return createOpenAiEmbeddingModel();
        }
        if (embeddingModelInstance.isUnsatisfied()) {
            throw new IllegalStateException("No embedding model bean matched the active provider: " + embeddingProvider);
        }
        if (embeddingModelInstance.isAmbiguous()) {
            throw new IllegalStateException("Multiple embedding model beans matched the active provider: " + embeddingProvider);
        }
        return embeddingModelInstance.get();
    }

    private EmbeddingModel createOpenAiEmbeddingModel() {
        String apiKey = openAiApiKey
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalStateException("OpenAI embedding provider requires quarkus.langchain4j.openai.api-key"));

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(openAiBaseUrl)
                .modelName(openAiModelName)
                .dimensions(targetVectorDimension)
                .maxRetries(openAiMaxRetries);

        openAiOrganizationId.ifPresent(builder::organizationId);
        openAiTimeout.ifPresent(builder::timeout);
        openAiLogRequests.ifPresent(builder::logRequests);
        openAiLogResponses.ifPresent(builder::logResponses);
        openAiUser.ifPresent(builder::user);

        return builder.build();
    }

}
