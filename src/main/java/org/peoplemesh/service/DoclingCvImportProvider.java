package org.peoplemesh.service;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.InputStream;
import java.util.UUID;

@LookupIfProperty(name = "peoplemesh.cv-import.provider", stringValue = "docling", lookupIfMissing = true)
@ApplicationScoped
public class DoclingCvImportProvider implements CvImportProvider {

    private static final Logger LOG = Logger.getLogger(DoclingCvImportProvider.class);
    private static final String KEY = "docling";
    private static final String SOURCE = "cv_docling_llm";

    @Inject
    DoclingCvParser doclingCvParser;

    @Inject
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public ProfileSchema extractProfile(InputStream content, String fileName, UUID userId) {
        long parseStart = System.currentTimeMillis();
        String markdown = doclingCvParser.parseToMarkdown(content, fileName)
                .orElseThrow(() -> new IllegalStateException("Failed to parse document"));
        long parseElapsed = System.currentTimeMillis() - parseStart;

        LOG.infof("CV parse completed: userId=%s provider=%s markdownSize=%d elapsedMs=%d",
                userId, key(), markdown.length(), parseElapsed);

        long llmStart = System.currentTimeMillis();
        ProfileSchema parsed = cvLlmProfileStructuringService.extractProfile(markdown);
        long llmElapsed = System.currentTimeMillis() - llmStart;
        if (parsed == null) {
            throw new IllegalStateException("CV LLM extraction returned null schema");
        }

        LOG.infof("CV structuring completed: userId=%s provider=%s elapsedMs=%d",
                userId, key(), llmElapsed);
        return parsed;
    }
}
