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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        assertArrayEquals(expected, service.generateEmbedding("hello"));
    }

}
