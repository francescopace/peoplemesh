package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.InputStream;
import java.util.UUID;

@ApplicationScoped
public class CvImportService {

    private static final Logger LOG = Logger.getLogger(CvImportService.class);

    @Inject
    DoclingCvParser doclingCvParser;

    @Inject
    ProfileStructuringLlm profileStructuringLlm;

    public record CvImportResult(ProfileSchema schema, String source) {}

    public CvImportResult parseCv(InputStream content, String fileName, long fileSize, UUID userId) {
        LOG.infof("CV import started: userId=%s size=%d", userId, fileSize);

        long parseStart = System.currentTimeMillis();
        String markdown = doclingCvParser.parseToMarkdown(content, fileName).orElse(null);
        long parseElapsed = System.currentTimeMillis() - parseStart;

        if (markdown == null) {
            LOG.warnf("CV parse returned empty: userId=%s elapsedMs=%d", userId, parseElapsed);
            throw new IllegalStateException("Failed to parse document");
        }
        LOG.infof("CV parse completed: userId=%s markdownSize=%d elapsedMs=%d",
                userId, markdown.length(), parseElapsed);

        long llmStart = System.currentTimeMillis();
        ProfileSchema parsed = profileStructuringLlm.extractProfile(markdown).orElse(null);
        long llmElapsed = System.currentTimeMillis() - llmStart;

        if (parsed == null) {
            LOG.warnf("CV structuring returned empty: userId=%s elapsedMs=%d", userId, llmElapsed);
            throw new IllegalStateException("Failed to extract profile from CV");
        }
        LOG.infof("CV structuring completed: userId=%s elapsedMs=%d", userId, llmElapsed);

        return new CvImportResult(parsed, CvProfileMergeService.SOURCE_CV);
    }
}
