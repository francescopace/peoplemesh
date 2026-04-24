package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@ApplicationScoped
public class CvImportService {

    private static final Logger LOG = Logger.getLogger(CvImportService.class);
    private static final String SOURCE_CV = "cv_docling_llm";

    @Inject
    DoclingCvParser doclingCvParser;

    @Inject
    CvLlmProfileStructuringService cvLlmProfileStructuringService;

    public record CvImportResult(ProfileSchema schema, String source) {}

    public CvImportResult importFromUpload(Path filePath, String fileName, long fileSize, long maxFileSize, UUID userId) {
        if (filePath == null) {
            throw new ValidationBusinessException("Missing file");
        }
        if (fileSize > maxFileSize) {
            throw new BusinessException(413, "Payload Too Large", "File exceeds maximum size");
        }
        try (InputStream stream = Files.newInputStream(filePath)) {
            return parseCv(stream, fileName, fileSize, userId);
        } catch (IllegalStateException e) {
            throw new BusinessException(502, "Bad Gateway", "CV processing failed");
        } catch (Exception e) {
            LOG.error("CV upload processing failed", e);
            throw new BusinessException(500, "Internal Server Error", "Error processing file");
        }
    }

    public CvImportResult parseCv(InputStream content, String fileName, long fileSize, UUID userId) {
        LOG.infof("CV import started: userId=%s size=%d", userId, fileSize);

        long parseStart = System.currentTimeMillis();
        String markdown = doclingCvParser.parseToMarkdown(content, fileName)
                .orElseThrow(() -> new IllegalStateException("Failed to parse document"));
        long parseElapsed = System.currentTimeMillis() - parseStart;

        LOG.infof("CV parse completed: userId=%s markdownSize=%d elapsedMs=%d",
                userId, markdown.length(), parseElapsed);

        long llmStart = System.currentTimeMillis();
        ProfileSchema parsed = cvLlmProfileStructuringService.extractProfile(markdown);
        long llmElapsed = System.currentTimeMillis() - llmStart;
        if (parsed == null) {
            throw new IllegalStateException("CV LLM extraction returned null schema");
        }

        LOG.infof("CV structuring completed: userId=%s elapsedMs=%d", userId, llmElapsed);

        return new CvImportResult(parsed, SOURCE_CV);
    }
}
