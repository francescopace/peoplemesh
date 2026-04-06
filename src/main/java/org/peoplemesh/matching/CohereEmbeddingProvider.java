package org.peoplemesh.matching;

import io.quarkus.arc.lookup.LookupIfProperty;
import org.peoplemesh.config.AppConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

@ApplicationScoped
@LookupIfProperty(name = "peoplemesh.embedding.provider", stringValue = "cohere")
public class CohereEmbeddingProvider implements EmbeddingProvider {

    private static final Logger LOG = Logger.getLogger(CohereEmbeddingProvider.class);
    private static final String API_URL = "https://api.cohere.ai/v1/embed";
    private static final int MAX_RETRIES = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    AppConfig config;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public float[] embed(String text) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("texts", List.of(text));
                put("model", config.embedding().cohere().model());
                put("input_type", "search_document");
            }});
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Cohere request", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + config.embedding().cohere().apiKey().orElse(""))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES) {
                        LOG.warnf("Cohere API returned %d, retrying (%d/%d)",
                                response.statusCode(), attempt + 1, MAX_RETRIES);
                        Thread.sleep(1000L * (attempt + 1));
                        continue;
                    }
                    throw new RuntimeException("Cohere API error after retries: " + response.statusCode());
                }

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Cohere API error: " + response.statusCode());
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.get("embeddings").get(0);
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                return vector;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while calling Cohere API", e);
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    LOG.warnf("Cohere API I/O error, retrying (%d/%d): %s",
                            attempt + 1, MAX_RETRIES, e.getMessage());
                    try { Thread.sleep(1000L * (attempt + 1)); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    continue;
                }
                throw new RuntimeException("Failed to generate embedding via Cohere after retries", e);
            }
        }
        throw new RuntimeException("Cohere embedding failed: exhausted retries");
    }

    @Override
    public int dimension() {
        return config.embedding().dimension();
    }
}
