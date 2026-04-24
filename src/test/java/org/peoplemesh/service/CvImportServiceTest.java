package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvImportServiceTest {

    @Mock
    DoclingCvParser doclingCvParser;

    @Mock
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    @InjectMocks
    CvImportService cvImportService;

    @Test
    void parseCv_success_returnsResult() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("cv content".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), eq("resume.pdf"))).thenReturn(Optional.of("# Markdown CV"));
        ProfileSchema schema = mock(ProfileSchema.class);
        when(cvLlmProfileStructuringService.extractProfile("# Markdown CV")).thenReturn(schema);

        CvImportService.CvImportResult result = cvImportService.parseCv(is, "resume.pdf", 1024, userId);

        assertSame(schema, result.schema());
        assertEquals("cv_docling_llm", result.source());
    }

    @Test
    void parseCv_parseReturnsEmpty_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
        assertTrue(ex.getMessage().contains("parse"));
    }

    @Test
    void parseCv_structuringReturnsEmpty_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.of("markdown"));
        when(cvLlmProfileStructuringService.extractProfile("markdown"))
                .thenThrow(new IllegalStateException("Failed to extract profile from CV"));

        assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
    }

    @Test
    void parseCv_structuringReturnsNull_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.of("markdown"));
        when(cvLlmProfileStructuringService.extractProfile("markdown")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> cvImportService.parseCv(is, "file.pdf", 100, userId));
    }
}
