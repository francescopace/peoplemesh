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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class ProfileService {

    private static final Logger LOG = Logger.getLogger(ProfileService.class);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService audit;

    @Inject
    ConsentService consentService;

    @Inject
    NodeRepository nodeRepository;

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
        applySchemaToNode(node, schema);
        applyEmbeddingIfConsented(node, userId);
        persistNode(node);

        audit.log(userId, "PROFILE_UPDATED", "peoplemesh_upsert_profile");
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
            String locale,
            String company
    ) {
        MeshNode node = findNodeById(nodeId)
                .filter(n -> n.nodeType == NodeType.USER)
                .orElseGet(() -> getOrCreateUserNode(nodeId));

        if (node.structuredData == null) {
            node.structuredData = new LinkedHashMap<>();
        }
        Map<String, Object> sd = node.structuredData;

        String normalizedDisplay = normalizeText(displayName);
        String normalizedFirst = normalizeText(firstName);
        String normalizedLast = normalizeText(lastName);
        String normalizedEmail = normalizeText(email);

        if (normalizedDisplay == null) {
            normalizedDisplay = joinName(normalizedFirst, normalizedLast);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> provenance = sd.containsKey("field_provenance")
                ? new HashMap<>((Map<String, String>) sd.get("field_provenance"))
                : new HashMap<>();

        if (normalizedDisplay != null) {
            node.title = normalizedDisplay;
            provenance.put("identity.display_name", provider);
        }
        if (normalizedFirst != null) {
            sd.put("first_name", normalizedFirst);
            provenance.put("identity.first_name", provider);
        }
        if (normalizedLast != null) {
            sd.put("last_name", normalizedLast);
            provenance.put("identity.last_name", provider);
        }
        if (normalizedEmail != null) {
            sd.put("email", normalizedEmail);
            provenance.put("identity.email", provider);
            if (node.externalId == null || node.externalId.isBlank()) {
                node.externalId = normalizedEmail;
            }
        }
        if (photoUrl != null && !photoUrl.isBlank() && sd.get("avatar_url") == null) {
            sd.put("avatar_url", photoUrl.trim());
            provenance.put("identity.photo_url", provider);
        }
        if (locale != null && !locale.isBlank() && sd.get("locale") == null) {
            sd.put("locale", locale.trim());
            provenance.put("identity.locale", provider);
        }
        if (company != null && !company.isBlank() && sd.get("company") == null) {
            sd.put("company", company.trim());
            provenance.put("identity.company", provider);
        }

        sd.put("field_provenance", provenance);

        applyEmbeddingIfConsented(node, nodeId);
        persistNode(node);
        return node;
    }

    @Transactional
    public void applySelectiveImport(UUID userId, ProfileSchema selectedFields, String source) {
        MeshNode node = getOrCreateUserNode(userId);

        Map<String, Object> sd = node.structuredData != null
                ? new LinkedHashMap<>(node.structuredData) : new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, String> provenance = sd.containsKey("field_provenance")
                ? new HashMap<>((Map<String, String>) sd.get("field_provenance"))
                : new HashMap<>();

        if (selectedFields.identity() != null) {
            var id = selectedFields.identity();
            boolean googleIdentity = provenance.containsValue("google")
                    && provenance.entrySet().stream()
                            .anyMatch(e -> e.getKey().startsWith("identity.") && "google".equals(e.getValue()));

            if (!googleIdentity) {
                if (hasText(id.displayName())) { node.title = normalizeText(id.displayName()); provenance.put("identity.display_name", source); }
                if (hasText(id.firstName())) { sd.put("first_name", normalizeText(id.firstName())); provenance.put("identity.first_name", source); }
                if (hasText(id.lastName())) { sd.put("last_name", normalizeText(id.lastName())); provenance.put("identity.last_name", source); }
                if (hasText(id.email())) { sd.put("email", normalizeText(id.email())); provenance.put("identity.email", source); }
                if (hasText(id.company())) { sd.put("company", id.company().trim()); provenance.put("identity.company", source); }
                if (hasText(id.photoUrl())) { sd.put("avatar_url", id.photoUrl().trim()); provenance.put("identity.photo_url", source); }
                if (hasText(id.locale())) { sd.put("locale", id.locale().trim()); provenance.put("identity.locale", source); }
            }
        }

        if (selectedFields.professional() != null) {
            var p = selectedFields.professional();
            if (hasValue(p.roles())) { node.description = String.join(",", p.roles()); provenance.put("professional.roles", source); }
            if (p.seniority() != null) { sd.put("seniority", p.seniority().name()); provenance.put("professional.seniority", source); }
            if (hasValue(p.industries())) { sd.put("industries", String.join(",", p.industries())); provenance.put("professional.industries", source); }
            if (hasValue(p.skillsTechnical())) { node.tags = new ArrayList<>(p.skillsTechnical()); provenance.put("professional.skills_technical", source); }
            if (hasValue(p.skillsSoft())) { sd.put("skills_soft", p.skillsSoft()); provenance.put("professional.skills_soft", source); }
            if (hasValue(p.toolsAndTech())) { sd.put("tools_and_tech", p.toolsAndTech()); provenance.put("professional.tools_and_tech", source); }
            if (hasValue(p.languagesSpoken())) { sd.put("languages_spoken", p.languagesSpoken()); provenance.put("professional.languages_spoken", source); }
            if (p.workModePreference() != null) { sd.put("work_mode", p.workModePreference().name()); provenance.put("professional.work_mode_preference", source); }
            if (p.employmentType() != null) { sd.put("employment_type", p.employmentType().name()); provenance.put("professional.employment_type", source); }
        }

        if (selectedFields.interestsProfessional() != null) {
            var ip = selectedFields.interestsProfessional();
            if (hasValue(ip.topicsFrequent())) { sd.put("topics_frequent", ip.topicsFrequent()); provenance.put("interests_professional.topics_frequent", source); }
            if (hasValue(ip.learningAreas())) { sd.put("learning_areas", ip.learningAreas()); provenance.put("interests_professional.learning_areas", source); }
            if (hasValue(ip.projectTypes())) { sd.put("project_types", ip.projectTypes()); provenance.put("interests_professional.project_types", source); }
        }

        if (selectedFields.geography() != null) {
            var g = selectedFields.geography();
            if (hasText(g.country())) { node.country = g.country(); provenance.put("geography.country", source); }
            if (hasText(g.city())) { sd.put("city", g.city()); provenance.put("geography.city", source); }
            if (hasText(g.timezone())) { sd.put("timezone", g.timezone()); provenance.put("geography.timezone", source); }
        }

        sd.put("field_provenance", provenance);
        node.structuredData = sd;
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
                        roles != null ? List.of(roles.split(",")) : null,
                        parseEnum(Seniority.class, seniority),
                        industries != null ? List.of(industries.split(",")) : null,
                        node.tags, sdList(sd, "skills_soft"), sdList(sd, "tools_and_tech"),
                        sdList(sd, "languages_spoken"),
                        parseEnum(WorkMode.class, sdString(sd, "work_mode")),
                        parseEnum(EmploymentType.class, sdString(sd, "employment_type")),
                        sdString(sd, "slack_handle")
                ),
                new ProfileSchema.InterestsInfo(
                        sdList(sd, "topics_frequent"), sdList(sd, "learning_areas"),
                        sdList(sd, "project_types")
                ),
                new ProfileSchema.PersonalInfo(
                        sdList(sd, "hobbies"), sdList(sd, "sports"),
                        sdList(sd, "education"), sdList(sd, "causes"),
                        sdList(sd, "personality_tags"), sdList(sd, "music_genres"),
                        sdList(sd, "book_genres")
                ),
                new ProfileSchema.GeographyInfo(node.country, sdString(sd, "city"), sdString(sd, "timezone")),
                provenance != null ? Map.copyOf(provenance) : null,
                new ProfileSchema.IdentityInfo(
                        node.title,
                        sdString(sd, "first_name"),
                        sdString(sd, "last_name"),
                        sdString(sd, "email"),
                        sdString(sd, "avatar_url"),
                        sdString(sd, "locale"),
                        sdString(sd, "company")
                )
        );
    }

    // === Schema -> Node ===

    private void applySchemaToNode(MeshNode node, ProfileSchema schema) {
        Map<String, Object> sd = node.structuredData != null
                ? new LinkedHashMap<>(node.structuredData) : new LinkedHashMap<>();

        if (schema.profileVersion() != null) sd.put("profile_version", schema.profileVersion());

        if (schema.professional() != null) {
            var p = schema.professional();
            if (p.roles() != null) node.description = String.join(",", p.roles());
            if (p.seniority() != null) sd.put("seniority", p.seniority().name());
            if (p.industries() != null) sd.put("industries", String.join(",", p.industries()));
            if (p.skillsTechnical() != null) node.tags = new ArrayList<>(p.skillsTechnical());
            if (p.skillsSoft() != null) sd.put("skills_soft", p.skillsSoft());
            if (p.toolsAndTech() != null) sd.put("tools_and_tech", p.toolsAndTech());
            if (p.languagesSpoken() != null) sd.put("languages_spoken", p.languagesSpoken());
            if (p.workModePreference() != null) sd.put("work_mode", p.workModePreference().name());
            if (p.employmentType() != null) sd.put("employment_type", p.employmentType().name());
            if (p.slackHandle() != null) sd.put("slack_handle", p.slackHandle());
        }

        if (schema.interestsProfessional() != null) {
            var ip = schema.interestsProfessional();
            if (ip.topicsFrequent() != null) sd.put("topics_frequent", ip.topicsFrequent());
            if (ip.learningAreas() != null) sd.put("learning_areas", ip.learningAreas());
            if (ip.projectTypes() != null) sd.put("project_types", ip.projectTypes());
        }

        if (schema.personal() != null) {
            var pe = schema.personal();
            if (pe.hobbies() != null) sd.put("hobbies", pe.hobbies());
            if (pe.sports() != null) sd.put("sports", pe.sports());
            if (pe.education() != null) sd.put("education", pe.education());
            if (pe.causes() != null) sd.put("causes", pe.causes());
            if (pe.personalityTags() != null) sd.put("personality_tags", pe.personalityTags());
            if (pe.musicGenres() != null) sd.put("music_genres", pe.musicGenres());
            if (pe.bookGenres() != null) sd.put("book_genres", pe.bookGenres());
        }

        if (schema.geography() != null) {
            var g = schema.geography();
            if (g.country() != null) node.country = g.country();
            if (g.city() != null) sd.put("city", g.city());
            if (g.timezone() != null) sd.put("timezone", g.timezone());
        }

        if (schema.fieldProvenance() != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> prov = sd.containsKey("field_provenance")
                    ? new HashMap<>((Map<String, String>) sd.get("field_provenance"))
                    : new HashMap<>();
            prov.putAll(schema.fieldProvenance());
            sd.put("field_provenance", prov);
        }

        if (schema.identity() != null) {
            var id = schema.identity();
            if (id.displayName() != null) node.title = normalizeText(id.displayName());
            if (id.firstName() != null) sd.put("first_name", normalizeText(id.firstName()));
            if (id.lastName() != null) sd.put("last_name", normalizeText(id.lastName()));
            if (id.email() != null) sd.put("email", normalizeText(id.email()));
            if (id.photoUrl() != null && !id.photoUrl().isBlank()) sd.put("avatar_url", id.photoUrl().trim());
            if (id.locale() != null) sd.put("locale", id.locale().trim());
            if (id.company() != null) sd.put("company", id.company().trim());
        }

        node.structuredData = sd;
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
        try { return Enum.valueOf(clazz, value); } catch (Exception e) { return null; }
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

    private static String joinName(String firstName, String lastName) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        return firstName != null ? firstName : lastName;
    }
}
