package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrNull;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import org.peoplemesh.util.StringUtils;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;

import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.util.EmbeddingTextBuilder;
import org.peoplemesh.util.ProfileSchemaNormalization;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class ProfileService {

    private static final Logger LOG = Logger.getLogger(ProfileService.class);
    private static final String SOURCE_CV = "cv_docling_llm";
    private static final String SOURCE_GITHUB = "github";

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService audit;

    @Inject
    ConsentService consentService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    ProfileSkillUsageService profileSkillUsageService;

    public Optional<ProfileSchema> getProfile(UUID userId) {
        return findPublishedUserNode(userId)
                .map(this::toSchema);
    }

    public Optional<ProfileSchema> getPublicProfile(UUID nodeId) {
        return findNodeById(nodeId)
                .filter(n -> n.nodeType == NodeType.USER)
                .map(this::toSchema);
    }

    @Transactional
    public MeshNode upsertProfile(UUID userId, ProfileSchema schema) {
        LOG.debugf("action=upsertProfile userId=%s", userId);
        MeshNode node = getOrCreateUserNode(userId);
        Set<String> oldSkills = profileSkillUsageService.collectProfileSkills(node);
        applySchemaToNode(node, schema, false);
        Set<String> newSkills = profileSkillUsageService.normalizeSkillFields(node);
        profileSkillUsageService.syncUsageCounters(oldSkills, newSkills);
        applyEmbeddingIfConsented(node, userId);
        persistNode(node);

        audit.log(userId, "PROFILE_UPDATED", "peoplemesh_upsert_profile");
        return node;
    }

    @Transactional
    public MeshNode upsertProfileReplace(UUID userId, ProfileSchema schema) {
        LOG.debugf("action=upsertProfileReplace userId=%s", userId);
        MeshNode node = getOrCreateUserNode(userId);
        Set<String> oldSkills = profileSkillUsageService.collectProfileSkills(node);
        applySchemaToNode(node, schema, true);
        Set<String> newSkills = profileSkillUsageService.normalizeSkillFields(node);
        profileSkillUsageService.syncUsageCounters(oldSkills, newSkills);
        applyEmbeddingIfConsented(node, userId);
        persistNode(node);

        audit.log(userId, "PROFILE_UPDATED", "peoplemesh_patch_profile");
        return node;
    }

    @Transactional
    public MeshNode upsertProfileFromProvider(
            UUID nodeId,
            String provider,
            String displayName,
            String firstName,
            String lastName,
            String email,
            String photoUrl,
            String birthDate,
            String company
    ) {
        MeshNode node = findNodeById(nodeId)
                .filter(n -> n.nodeType == NodeType.USER)
                .orElseGet(() -> getOrCreateUserNode(nodeId));

        Map<String, Object> sd = mutableStructuredData(node);

        String normalizedDisplay = normalizeText(displayName);
        String normalizedFirst = normalizeText(firstName);
        String normalizedLast = normalizeText(lastName);
        String normalizedEmail = normalizeText(email);

        if (normalizedDisplay == null) {
            normalizedDisplay = joinName(normalizedFirst, normalizedLast);
        }

        Map<String, String> provenance = extractProvenance(sd);

        if (normalizedDisplay != null) {
            node.title = normalizedDisplay;
            provenance.put("identity.display_name", provider);
        }
        if (normalizedFirst != null) {
            putWithProvenance(sd, provenance, "first_name", normalizedFirst, "identity.first_name", provider);
        }
        if (normalizedLast != null) {
            putWithProvenance(sd, provenance, "last_name", normalizedLast, "identity.last_name", provider);
        }
        if (normalizedEmail != null) {
            putWithProvenance(sd, provenance, "email", normalizedEmail, "identity.email", provider);
            if (node.externalId == null || node.externalId.isBlank()) {
                node.externalId = normalizedEmail;
            }
        }
        putIfMissingWithProvenance(sd, provenance, "avatar_url", photoUrl, "identity.photo_url", provider);
        putIfMissingWithProvenance(sd, provenance, "birth_date", birthDate, "identity.birth_date", provider);
        putIfMissingWithProvenance(sd, provenance, "company", company, "identity.company", provider);

        sd.put("field_provenance", provenance);

        applyEmbeddingIfConsented(node, nodeId);
        persistNode(node);
        return node;
    }

    @Transactional
    public void applySelectiveImport(UUID userId, ProfileSchema selectedFields, String source) {
        MeshNode node = getOrCreateUserNode(userId);
        Set<String> oldSkills = profileSkillUsageService.collectProfileSkills(node);

        Map<String, Object> sd = copyStructuredData(node.structuredData);
        Map<String, String> provenance = extractProvenance(sd);

        if (selectedFields.identity() != null) {
            var id = selectedFields.identity();
            if (hasText(id.birthDate())) {
                putWithProvenance(sd, provenance, "birth_date", id.birthDate().trim(), "identity.birth_date", source);
            }
        }

        if (selectedFields.professional() != null) {
            var p = selectedFields.professional();
            String importedRole = firstNonBlankListValue(p.roles());
            if (importedRole != null && shouldReplaceScalarField(provenance, "professional.roles", source, normalizeText(node.description))) {
                node.description = importedRole;
                provenance.put("professional.roles", source);
            }
            if (p.seniority() != null && shouldReplaceScalarField(provenance, "professional.seniority", source, sdString(sd, "seniority"))) {
                putWithProvenance(sd, provenance, "seniority", p.seniority().name(), "professional.seniority", source);
            }
            if (hasValue(p.industries())) {
                List<String> mergedIndustries = mergeStringLists(
                        splitCommaSeparated(sdString(sd, "industries")),
                        p.industries(),
                        provenance.get("professional.industries"),
                        source,
                        ProfileSchema.MAX_INDUSTRIES,
                        ProfileSchema.MAX_INDUSTRY_LENGTH
                );
                if (hasValue(mergedIndustries) && shouldReplaceScalarField(provenance, "professional.industries", source, sdString(sd, "industries"))) {
                    putWithProvenance(sd, provenance, "industries", String.join(",", mergedIndustries), "professional.industries", source);
                }
            }
            if (hasValue(p.skillsTechnical())) {
                List<String> mergedSkills = mergeStringLists(
                        node.tags,
                        p.skillsTechnical(),
                        provenance.get("professional.skills_technical"),
                        source,
                        ProfileSchema.MAX_SKILLS_TECHNICAL,
                        ProfileSchema.MAX_SKILL_TECHNICAL_LENGTH
                );
                if (hasValue(mergedSkills)) {
                    node.tags = new ArrayList<>(mergedSkills);
                    provenance.put("professional.skills_technical", source);
                }
            }
            if (hasValue(p.skillsSoft())) {
                List<String> mergedSoftSkills = mergeStringLists(
                        sdList(sd, "skills_soft"),
                        p.skillsSoft(),
                        provenance.get("professional.skills_soft"),
                        source,
                        ProfileSchema.MAX_SKILLS_SOFT,
                        ProfileSchema.MAX_SKILL_SOFT_LENGTH
                );
                if (hasValue(mergedSoftSkills)) {
                    putWithProvenance(sd, provenance, "skills_soft", mergedSoftSkills, "professional.skills_soft", source);
                }
            }
            if (hasValue(p.toolsAndTech())) {
                List<String> mergedTools = mergeStringLists(
                        sdList(sd, "tools_and_tech"),
                        p.toolsAndTech(),
                        provenance.get("professional.tools_and_tech"),
                        source,
                        ProfileSchema.MAX_TOOLS_AND_TECH,
                        ProfileSchema.MAX_TOOL_AND_TECH_LENGTH
                );
                if (hasValue(mergedTools)) {
                    putWithProvenance(sd, provenance, "tools_and_tech", mergedTools, "professional.tools_and_tech", source);
                }
            }
            List<String> normalizedLanguages = normalizeLanguages(p.languagesSpoken());
            if (hasValue(normalizedLanguages)) {
                List<String> mergedLanguages = mergeLanguages(
                        normalizeLanguages(sdList(sd, "languages_spoken")),
                        normalizedLanguages,
                        provenance.get("professional.languages_spoken"),
                        source
                );
                if (hasValue(mergedLanguages)) {
                    putWithProvenance(sd, provenance, "languages_spoken", mergedLanguages, "professional.languages_spoken", source);
                }
            }
            if (p.workModePreference() != null && shouldReplaceScalarField(provenance, "professional.work_mode_preference", source, sdString(sd, "work_mode"))) {
                putWithProvenance(sd, provenance, "work_mode", p.workModePreference().name(), "professional.work_mode_preference", source);
            }
            if (p.employmentType() != null && shouldReplaceScalarField(provenance, "professional.employment_type", source, sdString(sd, "employment_type"))) {
                putWithProvenance(sd, provenance, "employment_type", p.employmentType().name(), "professional.employment_type", source);
            }
        }

        if (selectedFields.contacts() != null) {
            var c = selectedFields.contacts();
            if (hasText(c.slackHandle())) { putWithProvenance(sd, provenance, "slack_handle", normalizeText(c.slackHandle()), "contacts.slack_handle", source); }
            if (hasText(c.telegramHandle())) { putWithProvenance(sd, provenance, "telegram_handle", normalizeText(c.telegramHandle()), "contacts.telegram_handle", source); }
            if (hasText(c.mobilePhone())) { putWithProvenance(sd, provenance, "mobile_phone", normalizeText(c.mobilePhone()), "contacts.mobile_phone", source); }
            if (hasText(c.linkedinUrl())) { putWithProvenance(sd, provenance, "linkedin_url", c.linkedinUrl().trim(), "contacts.linkedin_url", source); }
        }

        if (selectedFields.interestsProfessional() != null) {
            var ip = selectedFields.interestsProfessional();
            if (hasValue(ip.learningAreas())) {
                List<String> mergedLearningAreas = mergeStringLists(
                        sdList(sd, "learning_areas"),
                        ip.learningAreas(),
                        provenance.get("interests_professional.learning_areas"),
                        source,
                        ProfileSchema.MAX_LEARNING_AREAS,
                        ProfileSchema.MAX_LEARNING_AREA_LENGTH
                );
                if (hasValue(mergedLearningAreas)) {
                    putWithProvenance(sd, provenance, "learning_areas", mergedLearningAreas, "interests_professional.learning_areas", source);
                }
            }
            if (hasValue(ip.projectTypes())) {
                List<String> mergedProjectTypes = mergeStringLists(
                        sdList(sd, "project_types"),
                        ip.projectTypes(),
                        provenance.get("interests_professional.project_types"),
                        source,
                        ProfileSchema.MAX_PROJECT_TYPES,
                        ProfileSchema.MAX_PROJECT_TYPE_LENGTH
                );
                if (hasValue(mergedProjectTypes)) {
                    putWithProvenance(sd, provenance, "project_types", mergedProjectTypes, "interests_professional.project_types", source);
                }
            }
        }

        if (selectedFields.personal() != null) {
            var pe = selectedFields.personal();
            if (hasValue(pe.hobbies())) { putWithProvenance(sd, provenance, "hobbies", pe.hobbies(), "personal.hobbies", source); }
            if (hasValue(pe.sports())) { putWithProvenance(sd, provenance, "sports", pe.sports(), "personal.sports", source); }
            if (hasValue(pe.education())) { putWithProvenance(sd, provenance, "education", pe.education(), "personal.education", source); }
            if (hasValue(pe.causes())) { putWithProvenance(sd, provenance, "causes", pe.causes(), "personal.causes", source); }
            if (hasValue(pe.personalityTags())) { putWithProvenance(sd, provenance, "personality_tags", pe.personalityTags(), "personal.personality_tags", source); }
            if (hasValue(pe.musicGenres())) { putWithProvenance(sd, provenance, "music_genres", pe.musicGenres(), "personal.music_genres", source); }
            if (hasValue(pe.bookGenres())) { putWithProvenance(sd, provenance, "book_genres", pe.bookGenres(), "personal.book_genres", source); }
        }

        if (selectedFields.geography() != null) {
            var g = selectedFields.geography();
            if (hasText(g.country()) && shouldReplaceScalarField(provenance, "geography.country", source, node.country)) {
                node.country = g.country();
                provenance.put("geography.country", source);
            }
            if (hasText(g.city()) && shouldReplaceScalarField(provenance, "geography.city", source, sdString(sd, "city"))) {
                putWithProvenance(sd, provenance, "city", g.city(), "geography.city", source);
            }
            if (hasText(g.timezone()) && shouldReplaceScalarField(provenance, "geography.timezone", source, sdString(sd, "timezone"))) {
                putWithProvenance(sd, provenance, "timezone", g.timezone(), "geography.timezone", source);
            }
        }

        sd.put("field_provenance", provenance);
        node.structuredData = sd;
        Set<String> newSkills = profileSkillUsageService.normalizeSkillFields(node);
        profileSkillUsageService.syncUsageCounters(oldSkills, newSkills);
        applyEmbeddingIfConsented(node, userId);
        persistNode(node);

        audit.log(userId, "PROFILE_SELECTIVE_IMPORT", "peoplemesh_selective_import_" + source);
    }

    // === Node <-> Schema conversion ===

    ProfileSchema toSchema(MeshNode node) {
        Map<String, Object> sd = node.structuredData != null ? node.structuredData : Collections.emptyMap();

        String roles = node.description;
        String seniority = sdString(sd, "seniority");
        String industries = sdString(sd, "industries");

        @SuppressWarnings("unchecked")
        Map<String, String> provenance = sd.containsKey("field_provenance")
                ? (Map<String, String>) sd.get("field_provenance") : null;

        return new ProfileSchema(
                sdString(sd, "profile_version"),
                node.createdAt,
                new ProfileSchema.ConsentInfo(true, node.createdAt,
                        List.of("professional_matching"), 365, true),
                new ProfileSchema.ProfessionalInfo(
                        hasText(roles) ? List.of(roles.split(",")) : null,
                        parseEnum(Seniority.class, seniority),
                        hasText(industries) ? List.of(industries.split(",")) : null,
                        node.tags, sdList(sd, "skills_soft"), sdList(sd, "tools_and_tech"),
                        normalizeLanguages(sdList(sd, "languages_spoken")),
                        parseEnum(WorkMode.class, sdString(sd, "work_mode")),
                        parseEnum(EmploymentType.class, sdString(sd, "employment_type"))
                ),
                new ProfileSchema.ContactsInfo(
                        sdString(sd, "slack_handle"),
                        sdString(sd, "telegram_handle"),
                        sdString(sd, "mobile_phone"),
                        sdString(sd, "linkedin_url")
                ),
                new ProfileSchema.InterestsInfo(
                        sdList(sd, "learning_areas"),
                        sdList(sd, "project_types")
                ),
                new ProfileSchema.PersonalInfo(
                        sdList(sd, "hobbies"), sdList(sd, "sports"),
                        sdList(sd, "education"), sdList(sd, "causes"),
                        sdList(sd, "personality_tags"), sdList(sd, "music_genres"),
                        sdList(sd, "book_genres")
                ),
                new ProfileSchema.GeographyInfo(
                        node.country,
                        sdString(sd, "city"),
                        sdString(sd, "timezone")
                ),
                provenance != null ? Map.copyOf(provenance) : null,
                new ProfileSchema.IdentityInfo(
                        node.title,
                        sdString(sd, "first_name"),
                        sdString(sd, "last_name"),
                        sdString(sd, "email"),
                        sdString(sd, "avatar_url"),
                        sdString(sd, "company"),
                        sdString(sd, "birth_date")
                )
        );
    }

    // === Schema -> Node ===

    private void applySchemaToNode(MeshNode node, ProfileSchema schema, boolean replaceNulls) {
        Map<String, Object> sd = node.structuredData != null
                ? new LinkedHashMap<>(node.structuredData) : new LinkedHashMap<>();
        Map<String, String> provenance = extractProvenance(sd);

        if (schema.profileVersion() != null) {
            sd.put("profile_version", schema.profileVersion());
        } else if (replaceNulls) {
            removeStructuredField(sd, provenance, "profile_version", null);
        }

        if (schema.professional() != null) {
            var p = schema.professional();
            if (p.roles() != null) {
                node.description = String.join(",", p.roles());
            } else if (replaceNulls) {
                node.description = "";
                provenance.remove("professional.roles");
            }

            if (p.seniority() != null) {
                sd.put("seniority", p.seniority().name());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "seniority", "professional.seniority");
            }

            if (p.industries() != null) {
                sd.put("industries", String.join(",", p.industries()));
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "industries", "professional.industries");
            }

            if (p.skillsTechnical() != null) {
                node.tags = new ArrayList<>(p.skillsTechnical());
            } else if (replaceNulls) {
                node.tags = new ArrayList<>();
                provenance.remove("professional.skills_technical");
            }

            if (p.skillsSoft() != null) {
                sd.put("skills_soft", p.skillsSoft());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "skills_soft", "professional.skills_soft");
            }

            if (p.toolsAndTech() != null) {
                sd.put("tools_and_tech", p.toolsAndTech());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "tools_and_tech", "professional.tools_and_tech");
            }

            List<String> normalizedLanguages = normalizeLanguages(p.languagesSpoken());
            if (normalizedLanguages != null) {
                sd.put("languages_spoken", normalizedLanguages);
            } else if (replaceNulls && p.languagesSpoken() == null) {
                removeStructuredField(sd, provenance, "languages_spoken", "professional.languages_spoken");
            }

            if (p.workModePreference() != null) {
                sd.put("work_mode", p.workModePreference().name());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "work_mode", "professional.work_mode_preference");
            }

            if (p.employmentType() != null) {
                sd.put("employment_type", p.employmentType().name());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "employment_type", "professional.employment_type");
            }
        } else if (replaceNulls) {
            clearProfessionalFields(node, sd, provenance);
        }

        if (schema.contacts() != null) {
            var c = schema.contacts();
            if (c.slackHandle() != null) {
                sd.put("slack_handle", c.slackHandle());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "slack_handle", "contacts.slack_handle");
            }
            if (c.telegramHandle() != null) {
                sd.put("telegram_handle", c.telegramHandle());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "telegram_handle", "contacts.telegram_handle");
            }
            if (c.mobilePhone() != null) {
                sd.put("mobile_phone", c.mobilePhone());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "mobile_phone", "contacts.mobile_phone");
            }
            if (c.linkedinUrl() != null) {
                sd.put("linkedin_url", c.linkedinUrl().trim());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "linkedin_url", "contacts.linkedin_url");
            }
        } else if (replaceNulls) {
            clearContactFields(sd, provenance);
        }

        if (schema.interestsProfessional() != null) {
            var ip = schema.interestsProfessional();
            if (ip.learningAreas() != null) {
                sd.put("learning_areas", ip.learningAreas());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "learning_areas", "interests_professional.learning_areas");
            }
            if (ip.projectTypes() != null) {
                sd.put("project_types", ip.projectTypes());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "project_types", "interests_professional.project_types");
            }
        } else if (replaceNulls) {
            clearProfessionalInterestFields(sd, provenance);
        }

        if (schema.personal() != null) {
            var pe = schema.personal();
            if (pe.hobbies() != null) {
                sd.put("hobbies", pe.hobbies());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "hobbies", "personal.hobbies");
            }
            if (pe.sports() != null) {
                sd.put("sports", pe.sports());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "sports", "personal.sports");
            }
            if (pe.education() != null) {
                sd.put("education", pe.education());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "education", "personal.education");
            }
            if (pe.causes() != null) {
                sd.put("causes", pe.causes());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "causes", "personal.causes");
            }
            if (pe.personalityTags() != null) {
                sd.put("personality_tags", pe.personalityTags());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "personality_tags", "personal.personality_tags");
            }
            if (pe.musicGenres() != null) {
                sd.put("music_genres", pe.musicGenres());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "music_genres", "personal.music_genres");
            }
            if (pe.bookGenres() != null) {
                sd.put("book_genres", pe.bookGenres());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "book_genres", "personal.book_genres");
            }
        } else if (replaceNulls) {
            clearPersonalFields(sd, provenance);
        }

        if (schema.geography() != null) {
            var g = schema.geography();
            if (g.country() != null) {
                node.country = g.country();
            } else if (replaceNulls) {
                node.country = null;
                provenance.remove("geography.country");
            }
            if (g.city() != null) {
                sd.put("city", g.city());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "city", "geography.city");
            }
            if (g.timezone() != null) {
                sd.put("timezone", g.timezone());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "timezone", "geography.timezone");
            }
        } else if (replaceNulls) {
            clearGeographyFields(node, sd, provenance);
        }

        if (!replaceNulls && schema.fieldProvenance() != null) {
            provenance.putAll(schema.fieldProvenance());
        } else if (replaceNulls && schema.fieldProvenance() == null) {
            provenance.clear();
        }

        if (schema.identity() != null) {
            var id = schema.identity();
            if (id.birthDate() != null) {
                sd.put("birth_date", id.birthDate().trim());
            } else if (replaceNulls) {
                removeStructuredField(sd, provenance, "birth_date", "identity.birth_date");
            }
        } else if (replaceNulls) {
            clearIdentityFields(sd, provenance);
        }

        if (provenance.isEmpty()) {
            sd.remove("field_provenance");
        } else {
            sd.put("field_provenance", provenance);
        }

        node.structuredData = sd;
    }

    private static void removeStructuredField(
            Map<String, Object> structuredData,
            Map<String, String> provenance,
            String field,
            String provenanceField
    ) {
        structuredData.remove(field);
        if (provenanceField != null) {
            provenance.remove(provenanceField);
        }
    }

    private static void clearProfessionalFields(
            MeshNode node,
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        node.description = "";
        node.tags = new ArrayList<>();
        provenance.remove("professional.roles");
        provenance.remove("professional.skills_technical");
        removeStructuredField(structuredData, provenance, "seniority", "professional.seniority");
        removeStructuredField(structuredData, provenance, "industries", "professional.industries");
        removeStructuredField(structuredData, provenance, "skills_soft", "professional.skills_soft");
        removeStructuredField(structuredData, provenance, "tools_and_tech", "professional.tools_and_tech");
        removeStructuredField(structuredData, provenance, "languages_spoken", "professional.languages_spoken");
        removeStructuredField(structuredData, provenance, "work_mode", "professional.work_mode_preference");
        removeStructuredField(structuredData, provenance, "employment_type", "professional.employment_type");
    }

    private static void clearContactFields(
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        removeStructuredField(structuredData, provenance, "slack_handle", "contacts.slack_handle");
        removeStructuredField(structuredData, provenance, "telegram_handle", "contacts.telegram_handle");
        removeStructuredField(structuredData, provenance, "mobile_phone", "contacts.mobile_phone");
        removeStructuredField(structuredData, provenance, "linkedin_url", "contacts.linkedin_url");
    }

    private static void clearProfessionalInterestFields(
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        removeStructuredField(structuredData, provenance, "learning_areas", "interests_professional.learning_areas");
        removeStructuredField(structuredData, provenance, "project_types", "interests_professional.project_types");
    }

    private static void clearPersonalFields(
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        removeStructuredField(structuredData, provenance, "hobbies", "personal.hobbies");
        removeStructuredField(structuredData, provenance, "sports", "personal.sports");
        removeStructuredField(structuredData, provenance, "education", "personal.education");
        removeStructuredField(structuredData, provenance, "causes", "personal.causes");
        removeStructuredField(structuredData, provenance, "personality_tags", "personal.personality_tags");
        removeStructuredField(structuredData, provenance, "music_genres", "personal.music_genres");
        removeStructuredField(structuredData, provenance, "book_genres", "personal.book_genres");
    }

    private static void clearGeographyFields(
            MeshNode node,
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        node.country = null;
        provenance.remove("geography.country");
        removeStructuredField(structuredData, provenance, "city", "geography.city");
        removeStructuredField(structuredData, provenance, "timezone", "geography.timezone");
    }

    private static void clearIdentityFields(
            Map<String, Object> structuredData,
            Map<String, String> provenance
    ) {
        removeStructuredField(structuredData, provenance, "birth_date", "identity.birth_date");
    }

    // === Helpers ===

    private void applyEmbeddingIfConsented(MeshNode node, UUID userId) {
        if (consentService.hasActiveConsent(userId, "embedding_processing")) {
            float[] newEmbedding = embeddingService.generateEmbedding(nodeToEmbeddingText(node));
            node.embedding = newEmbedding;
            node.searchable = newEmbedding != null;
        } else {
            LOG.warnf("Embedding skipped: userId=%s consent=embedding_processing not granted", userId);
            node.embedding = null;
            node.searchable = false;
        }
    }

    private MeshNode getOrCreateUserNode(UUID nodeId) {
        return findPublishedUserNode(nodeId)
                .orElseGet(() -> {
                    MeshNode n = new MeshNode();
                    n.id = nodeId;
                    n.nodeType = NodeType.USER;
                    n.title = "Anonymous";
                    n.description = "";
                    n.tags = new ArrayList<>();
                    n.structuredData = new LinkedHashMap<>();
                    n.searchable = true;
                    persistNode(n);
                    return n;
                });
    }

    private Optional<MeshNode> findPublishedUserNode(UUID nodeId) {
        return nodeRepository.findPublishedUserNode(nodeId);
    }

    private Optional<MeshNode> findNodeById(UUID nodeId) {
        return nodeRepository.findById(nodeId);
    }

    private void persistNode(MeshNode node) {
        nodeRepository.persist(node);
    }

    String nodeToEmbeddingText(MeshNode node) {
        return EmbeddingTextBuilder.buildText(node);
    }

    private static List<String> sdList(Map<String, Object> sd, String key) {
        return sdListOrNull(sd, key);
    }

    @SuppressWarnings("null")
    private static <T extends Enum<T>> T parseEnum(Class<T> clazz, String value) {
        if (value == null || value.isBlank()) return null;
        return Enum.valueOf(clazz, value);
    }

    private static String normalizeText(String value) {
        return StringUtils.normalizeText(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasValue(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private static boolean shouldReplaceScalarField(
            Map<String, String> provenance,
            String provenanceField,
            String incomingSource,
            String existingValue
    ) {
        if (!hasText(existingValue)) {
            return true;
        }
        return sourcePriority(incomingSource) >= sourcePriority(provenance.get(provenanceField));
    }

    private static List<String> normalizeLanguages(List<String> languages) {
        return ProfileSchemaNormalization.normalizeLanguages(
                languages,
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        );
    }

    private static List<String> mergeLanguages(
            List<String> existing,
            List<String> incoming,
            String existingSource,
            String incomingSource
    ) {
        List<String> ordered = orderBySourcePriority(existing, incoming, existingSource, incomingSource);
        return normalizeLanguages(ordered);
    }

    private static List<String> mergeStringLists(
            List<String> existing,
            List<String> incoming,
            String existingSource,
            String incomingSource,
            int maxItems,
            int maxItemLength
    ) {
        List<String> ordered = orderBySourcePriority(existing, incoming, existingSource, incomingSource);
        return ProfileSchemaNormalization.normalizeList(ordered, maxItems, maxItemLength);
    }

    private static List<String> orderBySourcePriority(
            List<String> existing,
            List<String> incoming,
            String existingSource,
            String incomingSource
    ) {
        List<String> merged = new ArrayList<>();
        boolean incomingFirst = sourcePriority(incomingSource) >= sourcePriority(existingSource);
        if (incomingFirst) {
            if (incoming != null) merged.addAll(incoming);
            if (existing != null) merged.addAll(existing);
        } else {
            if (existing != null) merged.addAll(existing);
            if (incoming != null) merged.addAll(incoming);
        }
        return merged;
    }

    private static int sourcePriority(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        if (SOURCE_CV.equals(source)) {
            return 3;
        }
        if (SOURCE_GITHUB.equals(source)) {
            return 1;
        }
        return 4;
    }

    private static List<String> splitCommaSeparated(String value) {
        return value == null ? Collections.emptyList() : StringUtils.splitCommaSeparated(value);
    }

    private static Map<String, Object> mutableStructuredData(MeshNode node) {
        if (node.structuredData == null) {
            node.structuredData = new LinkedHashMap<>();
        }
        return node.structuredData;
    }

    private static Map<String, Object> copyStructuredData(Map<String, Object> structuredData) {
        return structuredData != null ? new LinkedHashMap<>(structuredData) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractProvenance(Map<String, Object> structuredData) {
        return structuredData.containsKey("field_provenance")
                ? new HashMap<>((Map<String, String>) structuredData.get("field_provenance"))
                : new HashMap<>();
    }

    private static void putWithProvenance(
            Map<String, Object> structuredData,
            Map<String, String> provenance,
            String field,
            Object value,
            String provenanceField,
            String source
    ) {
        structuredData.put(field, value);
        provenance.put(provenanceField, source);
    }

    private static void putIfMissingWithProvenance(
            Map<String, Object> structuredData,
            Map<String, String> provenance,
            String field,
            String rawValue,
            String provenanceField,
            String source
    ) {
        if (!hasText(rawValue) || structuredData.get(field) != null) {
            return;
        }
        putWithProvenance(structuredData, provenance, field, rawValue.trim(), provenanceField, source);
    }

    private static String firstNonBlankListValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String joinName(String firstName, String lastName) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        return firstName != null ? firstName : lastName;
    }

}
