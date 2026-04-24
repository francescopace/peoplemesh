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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void generateEmbedding_nullOrBlank_returnsNull() {
        assertNull(service.generateEmbedding(null));
        assertNull(service.generateEmbedding(""));
        assertNull(service.generateEmbedding("   "));
    }

    @Test
    void generateEmbeddings_nullOrEmpty_returnsEmptyList() {
        assertTrue(service.generateEmbeddings(null).isEmpty());
        assertTrue(service.generateEmbeddings(List.of()).isEmpty());
    }

    @Test
    void generateEmbeddings_onlyBlankInputs_returnsNullEntries() {
        List<float[]> out = service.generateEmbeddings(List.of("", "   "));
        assertEquals(2, out.size());
        assertNull(out.get(0));
        assertNull(out.get(1));
    }

    @Test
    void generateEmbedding_contextOverflow_throws() {
        String longText = "x".repeat(5000);

        when(embeddingModel.embed(longText))
                .thenThrow(new RuntimeException("the input length exceeds the context length"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateEmbedding(longText));
        assertEquals("the input length exceeds the context length", ex.getMessage());
    }

    @Test
    void generateEmbedding_contextOverflow_throwsSingleFailure() {
        String longText = "z".repeat(5000);
        when(embeddingModel.embed(longText))
                .thenThrow(new RuntimeException("maximum context length exceeded"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateEmbedding(longText));
        assertTrue(ex.getMessage().contains("maximum context length"));
    }

    @Test
    void generateEmbeddings_batchFailure_throws() {
        when(embeddingModel.embedAll(anyList()))
                .thenThrow(new RuntimeException("batch request failed"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> service.generateEmbeddings(List.of("y".repeat(5000), "short text"))
        );
        assertEquals("batch request failed", ex.getMessage());
    }

    @Test
    void generateEmbeddings_sizeMismatch_throws() {
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of()));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.generateEmbeddings(List.of("fallback text"))
        );
        assertEquals("Embedding batch response size mismatch", ex.getMessage());
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
