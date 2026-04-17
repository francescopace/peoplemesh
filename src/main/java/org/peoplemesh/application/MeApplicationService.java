package org.peoplemesh.application;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.CvImportService;
import org.peoplemesh.service.MeService;
import org.peoplemesh.service.ProfileService;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MeApplicationService {

    @Inject
    UserResolver userResolver;

    @Inject
    ProfileService profileService;

    @Inject
    MeService meService;

    @Inject
    CvImportService cvImportService;

    @Inject
    AppConfig appConfig;

    public Optional<ProfileSchema> getCurrentProfile(SecurityIdentity identity) {
        if (identity.isAnonymous()) {
            return Optional.empty();
        }
        UUID userId = userResolver.resolveUserId();
        return profileService.getProfile(userId);
    }

    public Optional<Map<String, Object>> getIdentityPayload(SecurityIdentity identity) {
        return meService.resolveIdentityPayload(identity);
    }

    public ProfileSchema upsertCurrentProfile(ProfileSchema updates) {
        UUID userId = userResolver.resolveUserId();
        profileService.upsertProfile(userId, updates);
        return profileService.getProfile(userId).orElse(updates);
    }

    public Optional<ProfileSchema> applyImport(ProfileSchema selectedFields, String source) {
        UUID userId = userResolver.resolveUserId();
        meService.applySelectiveImport(userId, selectedFields, source);
        return profileService.getProfile(userId);
    }

    public CvImportService.CvImportResult parseCv(FileUpload file) {
        if (file == null) {
            throw new ValidationBusinessException("Missing file");
        }
        if (file.size() > appConfig.cvImport().maxFileSize()) {
            throw new ValidationBusinessException("File exceeds maximum size");
        }
        UUID userId = userResolver.resolveUserId();
        try (InputStream is = Files.newInputStream(file.filePath())) {
            return cvImportService.parseCv(is, file.fileName(), file.size(), userId);
        } catch (ValidationBusinessException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw new IllegalStateException("CV processing failed", e);
        } catch (Exception e) {
            throw new RuntimeException("Error processing file", e);
        }
    }
}
