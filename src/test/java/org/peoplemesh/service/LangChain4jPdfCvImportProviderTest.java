package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LangChain4jPdfCvImportProviderTest {

    @Mock
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    @InjectMocks
    LangChain4jPdfCvImportProvider langChain4jPdfCvImportProvider;

    @Test
    void extractProfile_withPdfCapableProvider_returnsSchema() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("pdf".getBytes());
        ProfileSchema schema = mock(ProfileSchema.class);
        when(cvLlmProfileStructuringService.extractProfileFromPdf(any(), eq("resume.pdf"))).thenReturn(schema);

        ProfileSchema result = langChain4jPdfCvImportProvider.extractProfile(is, "resume.pdf", userId);

        assertSame(schema, result);
    }

    @Test
    void extractProfile_propagatesUnderlyingFailure() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("pdf".getBytes());
        when(cvLlmProfileStructuringService.extractProfileFromPdf(any(), eq("resume.pdf")))
                .thenThrow(new IllegalStateException("PDF extraction failed"));

        assertThrows(IllegalStateException.class,
                () -> langChain4jPdfCvImportProvider.extractProfile(is, "resume.pdf", userId));
    }
}
