package org.peoplemesh.service;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvImportServiceTest {

    @Mock
    Instance<CvImportProvider> cvImportProviderInstance;

    @Mock
    CvImportProvider cvImportProvider;

    @InjectMocks
    CvImportService cvImportService;

    @Test
    void parseCv_success_returnsResult() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("cv content".getBytes());
        ProfileSchema schema = mock(ProfileSchema.class);
        when(cvImportProviderInstance.isUnsatisfied()).thenReturn(false);
        when(cvImportProviderInstance.isAmbiguous()).thenReturn(false);
        when(cvImportProviderInstance.get()).thenReturn(cvImportProvider);
        when(cvImportProvider.key()).thenReturn("docling");
        when(cvImportProvider.source()).thenReturn("cv_docling_llm");
        when(cvImportProvider.extractProfile(any(), eq("resume.pdf"), eq(userId))).thenReturn(schema);

        CvImportService.CvImportResult result = cvImportService.parseCv(is, "resume.pdf", 1024, userId);

        assertSame(schema, result.schema());
        assertEquals("cv_docling_llm", result.source());
    }

    @Test
    void parseCv_providerReturnsNull_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(cvImportProviderInstance.isUnsatisfied()).thenReturn(false);
        when(cvImportProviderInstance.isAmbiguous()).thenReturn(false);
        when(cvImportProviderInstance.get()).thenReturn(cvImportProvider);
        when(cvImportProvider.key()).thenReturn("docling");
        when(cvImportProvider.extractProfile(any(), eq("file.pdf"), eq(userId))).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
        assertTrue(ex.getMessage().contains("null schema"));
    }

    @Test
    void parseCv_providerThrows_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(cvImportProviderInstance.isUnsatisfied()).thenReturn(false);
        when(cvImportProviderInstance.isAmbiguous()).thenReturn(false);
        when(cvImportProviderInstance.get()).thenReturn(cvImportProvider);
        when(cvImportProvider.key()).thenReturn("langchain4j-pdf");
        when(cvImportProvider.extractProfile(any(), eq("file.pdf"), eq(userId)))
                .thenThrow(new IllegalStateException("provider failed"));

        assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
    }

    @Test
    void parseCv_noMatchingProvider_throwsClearIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(cvImportProviderInstance.isUnsatisfied()).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
        assertTrue(ex.getMessage().contains("Supported values"));
    }

    @Test
    void parseCv_ambiguousProvider_throwsClearIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(cvImportProviderInstance.isUnsatisfied()).thenReturn(false);
        when(cvImportProviderInstance.isAmbiguous()).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
        assertTrue(ex.getMessage().contains("Multiple"));
    }
}
