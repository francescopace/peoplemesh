package org.peoplemesh.service;

import org.peoplemesh.config.HashUtils;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProfileService {

    private static final Logger LOG = Logger.getLogger(ProfileService.class);

    @Inject
    EncryptionService encryption;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService audit;

    @Inject
    ConsentService consentService;

    /**
     * Submit or replace a profile. The consent token is validated with scope check,
     * and released back if the transaction fails so the user can retry.
     */
    @Transactional
    public UserProfile submitProfile(UUID userId, ProfileSchema schema, String consentToken, String ipAddress) {
        UUID tokenUserId = consentService.validateAndConsume(consentToken, "profile_storage");
        if (!tokenUserId.equals(userId)) {
            consentService.releaseToken(consentToken);
            throw new SecurityException("Consent token user mismatch");
        }

        try {
            encryption.createKeyIfAbsent(userId);

            consentService.recordConsent(userId, "professional_matching",
                    ipAddress != null ? HashUtils.sha256(ipAddress) : null);

            UserProfile profile = UserProfile.findActiveByUserId(userId)
                    .orElseGet(() -> {
                        UserProfile p = new UserProfile();
                        p.userId = userId;
                        return p;
                    });

            applySchema(profile, schema, userId);
            profile.embedding = embeddingService.generateEmbedding(profile);
            profile.generatedAt = schema.generatedAt() != null ? schema.generatedAt() : Instant.now();
            profile.lastActiveAt = Instant.now();
            profile.persist();

            audit.log(userId, "PROFILE_SUBMITTED", "peoplemesh_submit_profile");
            return profile;
        } catch (Exception e) {
            consentService.releaseToken(consentToken);
            throw e;
        }
    }

    public Optional<ProfileSchema> getProfile(UUID userId) {
        return UserProfile.findActiveByUserId(userId)
                .map(p -> toSchema(p, userId));
    }

    @Transactional
    public Optional<UserProfile> updateProfile(UUID userId, ProfileSchema updates) {
        return UserProfile.findActiveByUserId(userId)
                .map(profile -> {
                    applySchema(profile, updates, userId);
                    profile.embedding = embeddingService.generateEmbedding(profile);
                    profile.lastActiveAt = Instant.now();
                    profile.persist();
                    audit.log(userId, "PROFILE_UPDATED", "peoplemesh_update_my_profile");
                    return profile;
                });
    }

    @Transactional
    public boolean deleteProfile(UUID userId) {
        return UserProfile.findActiveByUserId(userId)
                .map(profile -> {
                    profile.deletedAt = Instant.now();
                    profile.searchable = false;
                    profile.embedding = null;
                    profile.persist();
                    audit.log(userId, "PROFILE_DELETED", "peoplemesh_delete_my_profile");
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void touchActivity(UUID userId) {
        UserProfile.findActiveByUserId(userId)
                .ifPresent(p -> {
                    p.lastActiveAt = Instant.now();
                    p.persist();
                });
    }

    private void applySchema(UserProfile profile, ProfileSchema schema, UUID userId) {
        if (schema.profileVersion() != null)
            profile.profileVersion = schema.profileVersion();

        if (schema.professional() != null) {
            var p = schema.professional();
            if (p.roles() != null)
                profile.rolesEncrypted = encryption.encrypt(userId,
                        String.join(",", p.roles()));
            if (p.seniority() != null)
                profile.seniority = p.seniority();
            if (p.industries() != null)
                profile.industriesEncrypted = encryption.encrypt(userId,
                        String.join(",", p.industries()));
            if (p.skillsTechnical() != null)
                profile.skillsTechnical = p.skillsTechnical();
            if (p.skillsSoft() != null)
                profile.skillsSoft = p.skillsSoft();
            if (p.toolsAndTech() != null)
                profile.toolsAndTech = p.toolsAndTech();
            if (p.languagesSpoken() != null)
                profile.languagesSpoken = p.languagesSpoken();
            if (p.workModePreference() != null)
                profile.workMode = p.workModePreference();
            if (p.employmentType() != null)
                profile.employmentType = p.employmentType();
        }

        if (schema.interestsProfessional() != null) {
            var ip = schema.interestsProfessional();
            if (ip.topicsFrequent() != null)
                profile.topicsFrequent = ip.topicsFrequent();
            if (ip.learningAreas() != null)
                profile.learningAreas = ip.learningAreas();
            if (ip.projectTypes() != null)
                profile.projectTypes = ip.projectTypes();
            if (ip.collaborationGoals() != null)
                profile.collaborationGoals = ip.collaborationGoals();
        }

        if (schema.geography() != null) {
            var g = schema.geography();
            if (g.country() != null)
                profile.country = g.country();
            if (g.city() != null)
                profile.cityEncrypted = encryption.encrypt(userId, g.city());
            if (g.timezone() != null)
                profile.timezone = g.timezone();
        }

        if (schema.privacy() != null) {
            var pr = schema.privacy();
            profile.showCity = pr.showCity();
            profile.showCountry = pr.showCountry();
            profile.searchable = pr.searchable();
            if (pr.contactVia() != null)
                profile.contactVia = pr.contactVia();
        }
    }

    private ProfileSchema toSchema(UserProfile p, UUID userId) {
        String rolesDecrypted = encryption.decrypt(userId, p.rolesEncrypted);
        String industriesDecrypted = encryption.decrypt(userId, p.industriesEncrypted);
        String cityDecrypted = encryption.decrypt(userId, p.cityEncrypted);

        return new ProfileSchema(
                p.profileVersion,
                p.generatedAt,
                new ProfileSchema.ConsentInfo(true, p.createdAt,
                        java.util.List.of("professional_matching"), 365, true),
                new ProfileSchema.ProfessionalInfo(
                        rolesDecrypted != null ? java.util.List.of(rolesDecrypted.split(",")) : null,
                        p.seniority,
                        industriesDecrypted != null ? java.util.List.of(industriesDecrypted.split(",")) : null,
                        p.skillsTechnical, p.skillsSoft, p.toolsAndTech, p.languagesSpoken,
                        p.workMode, p.employmentType
                ),
                new ProfileSchema.InterestsInfo(
                        p.topicsFrequent, p.learningAreas, p.projectTypes, p.collaborationGoals
                ),
                new ProfileSchema.GeographyInfo(p.country, cityDecrypted, p.timezone),
                new ProfileSchema.PrivacyInfo(p.showCity, p.showCountry, p.searchable, p.contactVia)
        );
    }
}
