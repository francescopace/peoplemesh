package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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

    @Inject
    Instance<CvImportProvider> cvImportProviderInstance;

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
        CvImportProvider provider = resolveProvider();
        LOG.infof("CV import provider selected: userId=%s provider=%s", userId, provider.key());

        ProfileSchema parsed = provider.extractProfile(content, fileName, userId);
        if (parsed == null) {
            throw new IllegalStateException("CV import provider returned null schema");
        }
        return new CvImportResult(parsed, provider.source());
    }

    private CvImportProvider resolveProvider() {
        if (cvImportProviderInstance.isUnsatisfied()) {
            throw new IllegalStateException(
                    "No CV import provider matched the current peoplemesh.cv-import.provider. "
                            + "Supported values: docling, openai"
            );
        }
        if (cvImportProviderInstance.isAmbiguous()) {
            throw new IllegalStateException("Multiple CV import providers matched the current configuration");
        }
        return cvImportProviderInstance.get();
    }
}
