package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.*;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;

import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.peoplemesh.repository.NodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class MatchingService {

    private static final Logger LOG = Logger.getLogger(MatchingService.class);

    private static final double WEIGHT_EMBEDDING = 0.55;
    private static final double WEIGHT_SKILLS = 0.30;
    private static final double WEIGHT_GEOGRAPHY = 0.15;

    private static final Map<String, String> CONTINENT_MAP = Map.ofEntries(
            Map.entry("IT", "EU"), Map.entry("DE", "EU"), Map.entry("FR", "EU"),
            Map.entry("ES", "EU"), Map.entry("NL", "EU"), Map.entry("PT", "EU"),
            Map.entry("BE", "EU"), Map.entry("AT", "EU"), Map.entry("CH", "EU"),
            Map.entry("GB", "EU"), Map.entry("IE", "EU"), Map.entry("PL", "EU"),
            Map.entry("SE", "EU"), Map.entry("NO", "EU"), Map.entry("DK", "EU"),
            Map.entry("FI", "EU"), Map.entry("CZ", "EU"), Map.entry("RO", "EU"),
            Map.entry("US", "NA"), Map.entry("CA", "NA"), Map.entry("MX", "NA"),
            Map.entry("BR", "SA"), Map.entry("AR", "SA"), Map.entry("CL", "SA"),
            Map.entry("JP", "AS"), Map.entry("KR", "AS"), Map.entry("CN", "AS"),
            Map.entry("IN", "AS"), Map.entry("SG", "AS"), Map.entry("TH", "AS"),
            Map.entry("AU", "OC"), Map.entry("NZ", "OC")
    );

    record CandidateRow(
            UUID nodeId, UUID userId, Seniority seniority,
            List<String> skillsTechnical, List<String> skillsSoft, List<String> toolsAndTech,
            WorkMode workMode, EmploymentType employmentType,
            List<String> topicsFrequent, List<String> learningAreas,
            String country, String timezone, Instant updatedAt,
            String city,
            double cosineSim,
            String displayName, String roles,
            List<String> hobbies, List<String> sports,
            List<String> causes,
            String avatarUrl,
            String slackHandle, String email,
            String telegramHandle, String mobilePhone
    ) {
        static CandidateRow fromNativeRow(Object[] row) {
            return new CandidateRow(
                    (UUID) row[0], (UUID) row[1],
                    MatchingUtils.parseEnum(Seniority.class, (String) row[2]),
                    MatchingUtils.parseArray(row[3]), MatchingUtils.parseArray(row[4]), MatchingUtils.parseArray(row[5]),
                    MatchingUtils.parseEnum(WorkMode.class, (String) row[6]),
                    MatchingUtils.parseEnum(EmploymentType.class, (String) row[7]),
                    MatchingUtils.parseArray(row[8]), MatchingUtils.parseArray(row[9]),
                    (String) row[10], (String) row[11], MatchingUtils.toInstant(row[12]),
                    (String) row[13],
                    ((Number) row[14]).doubleValue(),
                    (String) row[15], (String) row[16],
                    MatchingUtils.parseArray(row[17]), MatchingUtils.parseArray(row[18]),
                    MatchingUtils.parseArray(row[19]),
                    (String) row[20],
                    (String) row[21], (String) row[22],
                    (String) row[23], (String) row[24]
            );
        }

        List<String> combinedSkillsAndTools() {
            return MatchingUtils.combineLists(skillsTechnical, toolsAndTech);
        }
    }

    @Inject
    MeshNodeSearchRepository searchRepository;

    @Inject
    AppConfig config;

    @Inject
    ConsentService consentService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    SkillLevelResolutionService skillLevelResolutionService;

    @Inject
    SemanticSkillMatcher semanticSkillMatcher;

    private static List<String> parseCommaSeparatedRoles(String plain) {
        return MatchingUtils.splitCommaSeparated(plain);
    }


    private List<MatchResult> findMatches(
            UUID excludeUserId,
            float[] queryEmbedding,
            MatchFilters filters,
            List<String> referenceSkillPool,
            String referenceCountry,
            WorkMode referenceWorkMode,
            EmploymentType referenceEmploymentType) {
        if (queryEmbedding == null) {
            return Collections.emptyList();
        }

        int poolSize = config.matching().candidatePoolSize();
        List<Object[]> candidates = searchRepository.findUserCandidatesByEmbedding(
                queryEmbedding, excludeUserId, poolSize);
        Map<UUID, Map<String, Short>> skillLevelsByNode = loadSkillLevelsByNode(candidates, filters);

        List<String> mySkillPool = deduplicateTerms(referenceSkillPool);
        double semanticThreshold = config.search().skillMatchThreshold();
        List<MatchResult> results = new ArrayList<>();

        for (Object[] raw : candidates) {
            CandidateRow c = CandidateRow.fromNativeRow(raw);
            List<String> candidateSkillPool = deduplicateTerms(
                    MatchingUtils.combineLists(c.combinedSkillsAndTools(), c.skillsSoft()));

            List<String> matchedSkills = matchSkills(mySkillPool, candidateSkillPool, semanticThreshold);
            double skillsOverlap = mySkillPool.isEmpty() ? 0.0 : (double) matchedSkills.size() / mySkillPool.size();

            if (filters != null && filters.skillsWithLevel() != null && !filters.skillsWithLevel().isEmpty()) {
                double levelScore = MatchingUtils.computeLevelAwareCoverage(
                        filters.skillsWithLevel(),
                        candidateSkillPool,
                        skillLevelsByNode.get(c.nodeId()));
                skillsOverlap = Math.max(skillsOverlap, levelScore);
            }

            double geoScore = geographyScore(referenceCountry, c.country(), referenceWorkMode);

            if (filters != null && !passesFilters(filters, c.skillsTechnical(),
                    c.workMode(), c.employmentType(), c.country())) {
                continue;
            }

            double rawScore = c.cosineSim() * WEIGHT_EMBEDDING
                    + skillsOverlap * WEIGHT_SKILLS
                    + geoScore * WEIGHT_GEOGRAPHY;

            double decayedScore = applyDecay(rawScore, c.updatedAt());
            double decayMultiplier = rawScore == 0.0 ? 1.0 : decayedScore / rawScore;

            String geographyReason = geographyReason(referenceCountry, c.country(), referenceWorkMode);
            List<String> reasonCodes = new ArrayList<>(buildReasonCodes(c.cosineSim(), matchedSkills, geoScore, decayMultiplier));
            if (referenceEmploymentType != null) {
                double employmentMatch = employmentCompatibility(c.employmentType(), referenceEmploymentType);
                if (employmentMatch > 0.5) {
                    reasonCodes.add("EMPLOYMENT_COMPATIBLE");
                }
            }

            results.add(new MatchResult(
                    c.nodeId(),
                    MatchingUtils.round3(decayedScore),
                    c.displayName(),
                    c.avatarUrl(),
                    parseCommaSeparatedRoles(c.roles()),
                    c.seniority(),
                    c.skillsTechnical(),
                    c.toolsAndTech(),
                    c.skillsSoft(),
                    c.workMode(),
                    c.employmentType(),
                    c.topicsFrequent(),
                    c.learningAreas(),
                    c.hobbies(),
                    c.sports(),
                    c.causes(),
                    c.country(),
                    c.city(),
                    c.timezone(),
                    c.slackHandle(),
                    c.email(),
                    c.telegramHandle(),
                    c.mobilePhone(),
                    new MatchScoreBreakdown(
                            MatchingUtils.round3(c.cosineSim()),
                            MatchingUtils.round3(skillsOverlap),
                            MatchingUtils.round3(geoScore),
                            MatchingUtils.round3(rawScore),
                            MatchingUtils.round3(decayMultiplier),
                            MatchingUtils.round3(decayedScore),
                            matchedSkills,
                            geographyReason,
                            reasonCodes
                    )
            ));
        }

        results.sort(Comparator.comparingDouble(MatchResult::score).reversed());

        return results.stream()
                .limit(config.matching().resultLimit())
                .toList();
    }

    private Map<UUID, Map<String, Short>> loadSkillLevelsByNode(List<Object[]> candidates, MatchFilters filters) {
        if (filters == null || filters.skillsWithLevel() == null || filters.skillsWithLevel().isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> nodeIds = candidates.stream()
                .map(row -> (UUID) row[0])
                .toList();
        if (skillLevelResolutionService == null) {
            return Collections.emptyMap();
        }
        SkillLevelResolutionService.SkillLevels skillLevels = skillLevelResolutionService.resolveForNodeIds(nodeIds);
        if (skillLevels.levelsByNodeForScoring().isEmpty()) {
            return Collections.emptyMap();
        }
        return skillLevels.levelsByNodeForScoring();
    }

    /**
     * Unified match method: returns people + nodes in a single list (uses the caller's published profile).
     */
    public List<MeshMatchResult> findAllMatches(UUID userId, String typeFilter, String country) {
        if (!consentService.hasActiveConsent(userId, "professional_matching")) {
            return Collections.emptyList();
        }
        MeshNode myNode = nodeRepository.findPublishedUserNode(userId).orElse(null);
        if (myNode == null || myNode.embedding == null) {
            return Collections.emptyList();
        }
        List<String> referenceTags = MatchingUtils.combineLists(
                myNode.tags != null ? myNode.tags : Collections.emptyList(),
                sdListOrEmpty(myNode.structuredData, "tools_and_tech"));
        referenceTags = MatchingUtils.combineLists(referenceTags, sdListOrEmpty(myNode.structuredData, "skills_soft"));
        List<String> nodeReferenceTags = MatchingUtils.combineLists(referenceTags, sdListOrEmpty(myNode.structuredData, "hobbies"));
        nodeReferenceTags = MatchingUtils.combineLists(nodeReferenceTags, sdListOrEmpty(myNode.structuredData, "sports"));
        MatchFilters peopleFilters = new MatchFilters(null, null, null, country);
        return doFindAllMatches(
                userId,
                myNode.embedding,
                referenceTags,
                nodeReferenceTags,
                myNode.country,
                MatchingUtils.structuredWorkMode(myNode),
                MatchingUtils.structuredEmploymentType(myNode),
                peopleFilters,
                typeFilter,
                country);
    }

    /**
     * Unified matches using a caller-supplied embedding (e.g. job vector search). Reference tags and
     * geography fields default to empty / null when not profiling a specific {@link MeshNode}.
     */
    public List<MeshMatchResult> findAllMatches(
            UUID excludeUserId, float[] embedding,
            String typeFilter, String countryFilter) {
        if (!consentService.hasActiveConsent(excludeUserId, "professional_matching")) {
            return Collections.emptyList();
        }
        if (embedding == null) {
            return Collections.emptyList();
        }
        MatchFilters peopleFilters = new MatchFilters(null, null, null, countryFilter);
        return doFindAllMatches(
                excludeUserId,
                embedding,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                null,
                null,
                peopleFilters,
                typeFilter,
                countryFilter);
    }

    /**
     * Core unified matching: people + non-user nodes. Consent must be checked by public entry points only.
     * {@code referenceTags} is the overlap pool for people (skills/tools); {@code nodeReferenceTags} is the pool
     * for non-USER nodes (tags + hobbies + sports when profiling a user — same as historical behavior).
     */
    private List<MeshMatchResult> doFindAllMatches(
            UUID excludeUserId, float[] embedding,
            List<String> referenceTags, List<String> nodeReferenceTags,
            String referenceCountry, WorkMode referenceWorkMode,
            EmploymentType referenceEmploymentType,
            MatchFilters peopleFilters,
            String typeFilter, String countryFilter) {
        long start = System.currentTimeMillis();
        List<MeshMatchResult> all = new ArrayList<>();

        boolean wantPeople = typeFilter == null || typeFilter.isEmpty() || "PEOPLE".equalsIgnoreCase(typeFilter);
        boolean wantNodes = typeFilter == null || typeFilter.isEmpty() || !"PEOPLE".equalsIgnoreCase(typeFilter);

        if (wantPeople) {
            List<MatchResult> people = findMatches(
                    excludeUserId,
                    embedding,
                    peopleFilters,
                    referenceTags,
                    referenceCountry,
                    referenceWorkMode,
                    referenceEmploymentType);
            for (MatchResult m : people) {
                if (countryFilter != null && !countryFilter.isEmpty() && m.country() != null
                        && !countryFilter.equalsIgnoreCase(m.country())) {
                    continue;
                }
                List<String> tags = MatchingUtils.combineLists(
                        MatchingUtils.combineLists(m.hobbies(), m.sports()),
                        m.causes()
                );
                var bd = m.breakdown();
                all.add(new MeshMatchResult(
                        m.nodeId(), "PEOPLE", m.displayName(), null, m.avatarUrl(), tags,
                        m.country(), m.score(),
                        bd != null ? new MeshMatchResult.MeshMatchBreakdown(
                                bd.embeddingScore(), bd.skillsScore(), bd.geographyScore(),
                                bd.rawScore(), bd.decayMultiplier(), bd.finalScore(),
                                bd.matchedSkills() != null ? bd.matchedSkills() : Collections.emptyList(),
                                bd.geographyReason()
                        ) : null,
                        new MeshMatchResult.PersonDetails(
                                m.roles(),
                                m.seniority() != null ? m.seniority().name() : null,
                                m.skillsTechnical(), m.toolsAndTech(), m.skillsSoft(),
                                m.workMode() != null ? m.workMode().name() : null,
                                m.employmentType() != null ? m.employmentType().name() : null,
                                m.topicsFrequent(),
                                m.learningAreas(),
                                m.hobbies(), m.sports(), m.causes(),
                                m.city(),
                                m.timezone(),
                                m.slackHandle(),
                                m.email(),
                                m.telegramHandle(),
                                m.mobilePhone()
                        )
                ));
            }
        }

        if (wantNodes) {
            NodeType nodeType = null;
            if (typeFilter != null && !typeFilter.isEmpty() && !"PEOPLE".equalsIgnoreCase(typeFilter)) {
                try {
                    nodeType = NodeType.valueOf(typeFilter.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Collections.emptyList();
                }
            }
            List<NodeMatchResult> nodes = findNodeMatches(
                    excludeUserId,
                    embedding,
                    nodeReferenceTags,
                    referenceCountry,
                    referenceWorkMode,
                    nodeType,
                    countryFilter);
            for (NodeMatchResult n : nodes) {
                var bd = n.breakdown();
                all.add(new MeshMatchResult(
                        n.nodeId(), n.nodeType() != null ? n.nodeType().name() : "UNKNOWN",
                        n.title(), n.description(), null, n.tags(), n.country(), n.score(),
                        bd != null ? new MeshMatchResult.MeshMatchBreakdown(
                                bd.embeddingScore(), bd.tagsScore(), bd.geographyScore(),
                                bd.rawScore(), bd.decayMultiplier(), bd.finalScore(),
                                bd.matchedTags() != null ? bd.matchedTags() : Collections.emptyList(),
                                bd.geographyReason()
                        ) : null,
                        null
                ));
            }
        }

        all.sort(Comparator.comparingDouble(MeshMatchResult::score).reversed());
        int limit = config.matching().resultLimit();
        List<MeshMatchResult> result = all.size() > limit ? all.subList(0, limit) : all;
        LOG.debugf("action=matchAll userId=%s type=%s candidates=%d results=%d elapsedMs=%d",
                excludeUserId, typeFilter, all.size(), result.size(), System.currentTimeMillis() - start);
        return result;
    }

    private List<NodeMatchResult> findNodeMatches(
            UUID excludeUserId,
            float[] embedding,
            List<String> myTags,
            String myCountry,
            WorkMode myWorkMode,
            NodeType targetType,
            String country) {
        if (embedding == null) {
            return Collections.emptyList();
        }

        int poolSize = config.matching().candidatePoolSize();
        List<Object[]> rows = searchRepository.findNodeCandidatesByEmbedding(
                embedding, excludeUserId, targetType, poolSize);

        List<String> tagPool = myTags != null ? myTags : Collections.emptyList();

        List<NodeMatchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            String nodeCountry = (String) row[5];
            if (country != null && (nodeCountry == null || !country.equalsIgnoreCase(nodeCountry))) {
                continue;
            }

            double cosineSim = ((Number) row[7]).doubleValue();
            List<String> nodeTags = MatchingUtils.parseArray(row[4]);
            double tagsOverlap = MatchingUtils.jaccardSimilarity(tagPool, nodeTags);
            double geoScore = geographyScore(myCountry, nodeCountry, myWorkMode);

            double rawScore = cosineSim * WEIGHT_EMBEDDING + tagsOverlap * WEIGHT_SKILLS + geoScore * WEIGHT_GEOGRAPHY;

            Instant updatedAt = MatchingUtils.toInstant(row[6]);
            double decayedScore = applyDecay(rawScore, updatedAt);
            double decayMultiplier = rawScore == 0.0 ? 1.0 : decayedScore / rawScore;

            List<String> matchedTags = MatchingUtils.intersectCaseInsensitive(tagPool, nodeTags);
            String geoReason = geographyReason(myCountry, nodeCountry, myWorkMode);
            List<String> reasonCodes = new ArrayList<>();
            if (cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
            if (!matchedTags.isEmpty()) reasonCodes.add("TAGS_OVERLAP");
            if (geoScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
            if (decayMultiplier < 1.0) reasonCodes.add("RECENCY_DECAY_APPLIED");

            results.add(new NodeMatchResult(
                    (UUID) row[0],
                    MatchingUtils.parseEnum(NodeType.class, (String) row[1]),
                    (String) row[2],
                    (String) row[3],
                    nodeTags,
                    nodeCountry,
                    MatchingUtils.round3(decayedScore),
                    new NodeMatchBreakdown(
                            MatchingUtils.round3(cosineSim),
                            MatchingUtils.round3(tagsOverlap),
                            MatchingUtils.round3(geoScore),
                            MatchingUtils.round3(rawScore),
                            MatchingUtils.round3(decayMultiplier),
                            MatchingUtils.round3(decayedScore),
                            matchedTags,
                            geoReason,
                            reasonCodes
                    )
            ));
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(NodeMatchResult::score).reversed())
                .limit(config.matching().resultLimit())
                .toList();
    }


    double geographyScore(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return 1.0;
        if (myCountry == null || theirCountry == null) return 0.0;
        if (myCountry.equalsIgnoreCase(theirCountry)) return 1.0;
        if (sameContinent(myCountry, theirCountry)) return 0.5;
        return 0.0;
    }

    double applyDecay(double score, Instant updatedAt) {
        if (updatedAt == null) return score;
        long monthsOld = Duration.between(updatedAt, Instant.now()).toDays() / 30;
        if (monthsOld <= 6) return score;
        return score * Math.exp(-config.matching().decayLambda() * (monthsOld - 6));
    }

    private double employmentCompatibility(EmploymentType candidate, EmploymentType required) {
        if (candidate == null || required == null) return 0.5;
        if (candidate == EmploymentType.OPEN_TO_OFFERS || candidate == EmploymentType.LOOKING) return 1.0;
        return candidate == required ? 1.0 : 0.0;
    }

    private boolean passesFilters(
            MatchFilters filters,
            List<String> skillsTechnical,
            WorkMode workMode,
            EmploymentType employmentType,
            String country) {
        if (filters.country() != null && (country == null || !filters.country().equalsIgnoreCase(country))) {
            return false;
        }
        if (filters.workMode() != null && filters.workMode() != workMode) {
            return false;
        }
        if (filters.employmentType() != null && filters.employmentType() != employmentType) {
            return false;
        }
        if (filters.skillsTechnical() != null && !filters.skillsTechnical().isEmpty()) {
            if (MatchingUtils.intersectCaseInsensitive(filters.skillsTechnical(), skillsTechnical).isEmpty()) {
                return false;
            }
        }
        return true;
    }


    private String geographyReason(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return "remote_friendly";
        if (myCountry == null || theirCountry == null) return "location_unknown";
        if (myCountry.equalsIgnoreCase(theirCountry)) return "same_country";
        if (sameContinent(myCountry, theirCountry)) return "same_continent";
        return "different_region";
    }


    private boolean sameContinent(String a, String b) {
        String contA = CONTINENT_MAP.getOrDefault(a.toUpperCase(), "XX");
        String contB = CONTINENT_MAP.getOrDefault(b.toUpperCase(), "YY");
        return contA.equals(contB);
    }

    private static List<String> buildReasonCodes(double cosineSim, List<String> matchedSkills,
                                                   double geoScore, double decayMultiplier) {
        List<String> codes = new ArrayList<>();
        if (cosineSim >= 0.65) codes.add("SEMANTIC_SIMILARITY");
        if (!matchedSkills.isEmpty()) codes.add("SKILLS_OVERLAP");
        if (geoScore > 0) codes.add("LOCATION_COMPATIBLE");
        if (decayMultiplier < 1.0) codes.add("RECENCY_DECAY_APPLIED");
        return codes;
    }

    private static List<String> deduplicateTerms(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            if (seen.add(normalized)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private List<String> matchSkills(List<String> querySkills, List<String> candidateSkills, double threshold) {
        if (querySkills == null || querySkills.isEmpty() || candidateSkills == null || candidateSkills.isEmpty()) {
            return Collections.emptyList();
        }
        return semanticSkillMatcher.matchSkills(querySkills, candidateSkills, threshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
    }


    private record MatchResult(
            UUID nodeId, double score, String displayName, String avatarUrl,
            List<String> roles, Seniority seniority,
            List<String> skillsTechnical, List<String> toolsAndTech, List<String> skillsSoft,
            WorkMode workMode, EmploymentType employmentType,
            List<String> topicsFrequent, List<String> learningAreas,
            List<String> hobbies, List<String> sports, List<String> causes,
            String country, String city, String timezone,
            String slackHandle, String email, String telegramHandle, String mobilePhone,
            MatchScoreBreakdown breakdown
    ) {}

    private record MatchScoreBreakdown(
            double embeddingScore, double skillsScore, double geographyScore,
            double rawScore, double decayMultiplier, double finalScore,
            List<String> matchedSkills, String geographyReason, List<String> reasonCodes
    ) {}

    private record NodeMatchResult(
            UUID nodeId, NodeType nodeType, String title, String description,
            List<String> tags, String country, double score,
            NodeMatchBreakdown breakdown
    ) {}

    private record NodeMatchBreakdown(
            double embeddingScore, double tagsScore, double geographyScore,
            double rawScore, double decayMultiplier, double finalScore,
            List<String> matchedTags, String geographyReason, List<String> reasonCodes
    ) {}
}
