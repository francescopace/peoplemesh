package org.peoplemesh.service;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.InputStream;
import java.util.UUID;

@LookupIfProperty(name = "peoplemesh.cv-import.provider", stringValue = "openai")
@ApplicationScoped
public class LangChain4jPdfCvImportProvider implements CvImportProvider {

    private static final Logger LOG = Logger.getLogger(LangChain4jPdfCvImportProvider.class);
    private static final String KEY = "openai";
    private static final String SOURCE = "cv_openai_pdf_llm";

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
        long llmStart = System.currentTimeMillis();
        ProfileSchema parsed = cvLlmProfileStructuringService.extractProfileFromPdf(content, fileName);
        long llmElapsed = System.currentTimeMillis() - llmStart;
        if (parsed == null) {
            throw new IllegalStateException("CV PDF LLM extraction returned null schema");
        }

        LOG.infof("CV structuring completed: userId=%s provider=%s chatProvider=%s elapsedMs=%d",
                userId, key(), "openai-compatible", llmElapsed);
        return parsed;
    }
}
