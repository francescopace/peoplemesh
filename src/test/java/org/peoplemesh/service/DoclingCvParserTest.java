package org.peoplemesh.service;

import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import io.quarkiverse.docling.runtime.client.DoclingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoclingCvParserTest {

    @Mock
    DoclingService doclingService;

    @InjectMocks
    DoclingCvParser parser;

    @Test
    void parseToMarkdown_successfulParse_returnsMarkdown() throws IOException {
        InputStream is = new ByteArrayInputStream("cv".getBytes());
        InBodyConvertDocumentResponse response = mock(InBodyConvertDocumentResponse.class);
        DocumentResponse doc = mock(DocumentResponse.class);
        when(response.getDocument()).thenReturn(doc);
        when(doc.getMarkdownContent()).thenReturn("# Parsed CV");
        when(doclingService.convertFromInputStream(eq(is), eq("resume.pdf"), any()))
                .thenReturn(response);

        Optional<String> result = parser.parseToMarkdown(is, "resume.pdf");

        assertTrue(result.isPresent());
        assertEquals("# Parsed CV", result.get());
    }

    @Test
    void parseToMarkdown_nullDocument_returnsEmpty() throws IOException {
        InputStream is = new ByteArrayInputStream("cv".getBytes());
        InBodyConvertDocumentResponse response = mock(InBodyConvertDocumentResponse.class);
        doReturn(null).when(response).getDocument();
        when(doclingService.convertFromInputStream(any(), anyString(), any()))
                .thenReturn(response);

        Optional<String> result = parser.parseToMarkdown(is, "file.pdf");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseToMarkdown_nullMarkdownContent_returnsEmpty() throws IOException {
        InputStream is = new ByteArrayInputStream("cv".getBytes());
        InBodyConvertDocumentResponse response = mock(InBodyConvertDocumentResponse.class);
        DocumentResponse doc = mock(DocumentResponse.class);
        when(response.getDocument()).thenReturn(doc);
        when(doc.getMarkdownContent()).thenReturn(null);
        when(doclingService.convertFromInputStream(any(), anyString(), any()))
                .thenReturn(response);

        Optional<String> result = parser.parseToMarkdown(is, "file.pdf");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseToMarkdown_exception_returnsEmpty() throws IOException {
        InputStream is = new ByteArrayInputStream("cv".getBytes());
        when(doclingService.convertFromInputStream(any(), anyString(), any()))
                .thenThrow(new RuntimeException("Docling down"));

        Optional<String> result = parser.parseToMarkdown(is, "file.pdf");

        assertTrue(result.isEmpty());
    }
}
