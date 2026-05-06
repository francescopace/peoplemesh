package org.peoplemesh.service;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

@LookupIfProperty(name = "peoplemesh.cv-import.provider", stringValue = "langchain4j-pdf")
@ApplicationScoped
public class LangChain4jPdfCvImportProvider implements CvImportProvider {

    private static final Logger LOG = Logger.getLogger(LangChain4jPdfCvImportProvider.class);
    private static final String KEY = "langchain4j-pdf";
    private static final String SOURCE = "cv_langchain4j_pdf_llm";

    @Inject
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    @ConfigProperty(name = "quarkus.langchain4j.chat-model.provider")
    Optional<String> chatModelProvider;

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
        validateChatProvider();

        long llmStart = System.currentTimeMillis();
        ProfileSchema parsed = cvLlmProfileStructuringService.extractProfileFromPdf(content, fileName);
        long llmElapsed = System.currentTimeMillis() - llmStart;
        if (parsed == null) {
            throw new IllegalStateException("CV PDF LLM extraction returned null schema");
        }

        LOG.infof("CV structuring completed: userId=%s provider=%s chatProvider=%s elapsedMs=%d",
                userId, key(), chatModelProvider.orElse("unknown"), llmElapsed);
        return parsed;
    }

    private void validateChatProvider() {
        String provider = chatModelProvider.map(String::trim).orElse("");
        if ("ollama".equalsIgnoreCase(provider)) {
            throw new IllegalStateException(
                    "CV import provider 'langchain4j-pdf' requires a PDF-capable chat model provider; "
                            + "current quarkus.langchain4j.chat-model.provider=ollama"
            );
        }
    }
}
