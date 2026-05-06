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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoclingCvImportProviderTest {

    @Mock
    DoclingCvParser doclingCvParser;

    @Mock
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    @InjectMocks
    DoclingCvImportProvider doclingCvImportProvider;

    @Test
    void extractProfile_success_returnsSchema() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("cv content".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), eq("resume.pdf"))).thenReturn(Optional.of("# Markdown CV"));
        ProfileSchema schema = mock(ProfileSchema.class);
        when(cvLlmProfileStructuringService.extractProfile("# Markdown CV")).thenReturn(schema);

        ProfileSchema result = doclingCvImportProvider.extractProfile(is, "resume.pdf", userId);

        assertSame(schema, result);
    }

    @Test
    void extractProfile_parseReturnsEmpty_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> doclingCvImportProvider.extractProfile(is, "file.pdf", userId));
        assertTrue(ex.getMessage().contains("parse"));
    }

    @Test
    void extractProfile_structuringReturnsEmpty_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.of("markdown"));
        when(cvLlmProfileStructuringService.extractProfile("markdown"))
                .thenThrow(new IllegalStateException("Failed to extract profile from CV"));

        assertThrows(IllegalStateException.class,
                () -> doclingCvImportProvider.extractProfile(is, "file.pdf", userId));
    }

    @Test
    void extractProfile_structuringReturnsNull_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        InputStream is = new ByteArrayInputStream("data".getBytes());
        when(doclingCvParser.parseToMarkdown(any(), anyString())).thenReturn(Optional.of("markdown"));
        when(cvLlmProfileStructuringService.extractProfile("markdown")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> doclingCvImportProvider.extractProfile(is, "file.pdf", userId));
    }
}
