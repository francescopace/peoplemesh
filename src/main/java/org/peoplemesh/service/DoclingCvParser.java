package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Optional;
import io.quarkiverse.docling.runtime.client.DoclingService;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;

@ApplicationScoped
public class DoclingCvParser {

    private static final Logger LOG = Logger.getLogger(DoclingCvParser.class);

    @Inject
    DoclingService doclingService;

    public Optional<String> parseToMarkdown(InputStream fileStream, String filename) {
        long start = System.currentTimeMillis();
        try {
            LOG.infof("Docling parse started: file=%s", filename);
            ConvertDocumentResponse response = doclingService.convertFromInputStream(fileStream, filename,
                    OutputFormat.MARKDOWN);
            Optional<String> markdown = Optional.empty();
            if (response instanceof InBodyConvertDocumentResponse inBodyResponse) {
                DocumentResponse doc = inBodyResponse.getDocument();
                if (doc != null && doc.getMarkdownContent() != null) {
                    markdown = Optional.of(doc.getMarkdownContent());
                }
            }

            LOG.infof(
                    "Docling parse completed: file=%s success=%s markdownSize=%d elapsedMs=%d",
                    filename,
                    markdown.isPresent(),
                    markdown.map(String::length).orElse(0),
                    System.currentTimeMillis() - start
            );
            return markdown;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse CV with Docling");
            return Optional.empty();
        }
    }
}
