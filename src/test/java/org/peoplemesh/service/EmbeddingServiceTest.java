package org.peoplemesh.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    EmbeddingModel embeddingModel;

    private EmbeddingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new EmbeddingService();
        Field field = EmbeddingService.class.getDeclaredField("embeddingModel");
        field.setAccessible(true);
        field.set(service, embeddingModel);
    }

    @Test
    void generateEmbedding_delegatesToModel() {
        float[] expected = {0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("hello"))
                .thenReturn(Response.from(Embedding.from(expected)));
        float[] actual = service.generateEmbedding("hello");
        assertEquals(1024, actual.length);
        assertArrayEquals(expected, prefix(actual, expected.length));
    }

    @Test
    void generateEmbedding_retriesWithShorterTextOnContextOverflow() {
        String longText = "x".repeat(5000);
        String truncated = longText.substring(0, 3000);
        float[] expected = {0.4f, 0.5f};

        when(embeddingModel.embed(longText))
                .thenThrow(new RuntimeException("the input length exceeds the context length"));
        when(embeddingModel.embed(truncated))
                .thenReturn(Response.from(Embedding.from(expected)));

        float[] actual = service.generateEmbedding(longText);
        assertEquals(1024, actual.length);
        assertArrayEquals(expected, prefix(actual, expected.length));
    }

    @Test
    void generateEmbeddings_batchFailureFallsBackToSingleEmbeddingWithTruncation() {
        String longText = "y".repeat(5000);
        String truncated = longText.substring(0, 3000);
        float[] longExpected = {0.8f, 0.9f};
        float[] shortExpected = {0.1f, 0.2f};

        when(embeddingModel.embedAll(anyList()))
                .thenThrow(new RuntimeException("batch request failed"));
        when(embeddingModel.embed(longText))
                .thenThrow(new RuntimeException("input length exceeds context length"));
        when(embeddingModel.embed(truncated))
                .thenReturn(Response.from(Embedding.from(longExpected)));
        when(embeddingModel.embed("short text"))
                .thenReturn(Response.from(Embedding.from(shortExpected)));

        List<float[]> result = service.generateEmbeddings(List.of(longText, "short text"));

        assertEquals(2, result.size());
        assertEquals(1024, result.get(0).length);
        assertEquals(1024, result.get(1).length);
        assertArrayEquals(longExpected, prefix(result.get(0), longExpected.length));
        assertArrayEquals(shortExpected, prefix(result.get(1), shortExpected.length));
    }

    private static float[] prefix(float[] values, int size) {
        float[] out = new float[size];
        System.arraycopy(values, 0, out, 0, size);
        return out;
    }

}
