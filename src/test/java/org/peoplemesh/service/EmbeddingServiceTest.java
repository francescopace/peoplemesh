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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        float[] expected = vectorOfSize(384, 0.1f);
        when(embeddingModel.embed("hello"))
                .thenReturn(Response.from(Embedding.from(expected)));
        float[] actual = service.generateEmbedding("hello");
        assertEquals(384, actual.length);
        assertArrayEquals(expected, actual);
    }

    @Test
    void generateEmbedding_retriesWithShorterTextOnContextOverflow() {
        String longText = "x".repeat(5000);
        String truncated = longText.substring(0, 3000);
        float[] expected = vectorOfSize(384, 0.4f);

        when(embeddingModel.embed(longText))
                .thenThrow(new RuntimeException("the input length exceeds the context length"));
        when(embeddingModel.embed(truncated))
                .thenReturn(Response.from(Embedding.from(expected)));

        float[] actual = service.generateEmbedding(longText);
        assertEquals(384, actual.length);
        assertArrayEquals(expected, actual);
    }

    @Test
    void generateEmbeddings_batchFailureFallsBackToSingleEmbeddingWithTruncation() {
        String longText = "y".repeat(5000);
        String truncated = longText.substring(0, 3000);
        float[] longExpected = vectorOfSize(384, 0.8f);
        float[] shortExpected = vectorOfSize(384, 0.1f);

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
        assertEquals(384, result.get(0).length);
        assertEquals(384, result.get(1).length);
        assertArrayEquals(longExpected, result.get(0));
        assertArrayEquals(shortExpected, result.get(1));
    }

    @Test
    void generateEmbedding_dimensionMismatch_throws() {
        float[] wrongSize = vectorOfSize(383, 0.2f);
        when(embeddingModel.embed("bad"))
                .thenReturn(Response.from(Embedding.from(wrongSize)));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateEmbedding("bad"));
        assertEquals("Embedding dimension mismatch: expected 384, got 383", ex.getMessage());
    }

    private static float[] vectorOfSize(int size, float base) {
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = base + (i * 0.0001f);
        }
        return out;
    }

}
