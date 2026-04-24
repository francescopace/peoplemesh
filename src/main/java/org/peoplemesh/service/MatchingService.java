package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.*;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;

import org.peoplemesh.repository.MeshNodeSearchRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.util.GeographyUtils;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.StringUtils;

import java.util.*;

@ApplicationScoped
public class MatchingService {

    private static final Logger LOG = Logger.getLogger(MatchingService.class);

    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final double WEIGHT_EMBEDDING = 0.55;
    private static final double WEIGHT_SKILLS = 0.30;
    private static final double WEIGHT_GEOGRAPHY = 0.15;

    @Inject
    MeshNodeSearchRepository searchRepository;

    @Inject
    AppConfig config;

    @Inject
    ConsentService consentService;

    @Inject
    SemanticSkillMatcher semanticSkillMatcher;

    @Inject
    MatchingCandidateMapper candidateMapper;

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
        List<MatchingCandidateMapper.CandidateRow> candidates = searchRepository.findUserCandidatesByEmbedding(queryEmbedding, excludeUserId, poolSize)
                .stream()
                .map(candidateMapper::fromRepositoryRow)
                .toList();

        List<String> mySkillPool = SearchMatchingUtils.deduplicateTerms(referenceSkillPool);
        double semanticThreshold = config.skills().matchThreshold();
        List<MatchResult> results = new ArrayList<>();

        for (MatchingCandidateMapper.CandidateRow c : candidates) {
            List<String> candidateSkillPool = SearchMatchingUtils.deduplicateTerms(
                    MatchingUtils.combineLists(c.combinedSkillsAndTools(), c.skillsSoft()));

            List<String> matchedSkills = matchSkills(mySkillPool, candidateSkillPool, semanticThreshold);
            double skillsOverlap = mySkillPool.isEmpty() ? 0.0 : (double) matchedSkills.size() / mySkillPool.size();

            double geoScore = GeographyUtils.geographyScore(referenceCountry, c.country(), referenceWorkMode);

            if (filters != null && !passesFilters(filters, c.skillsTechnical(),
                    c.workMode(), c.employmentType(), c.country())) {
                continue;
            }

            double rawScore = c.cosineSim() * WEIGHT_EMBEDDING
                    + skillsOverlap * WEIGHT_SKILLS
                    + geoScore * WEIGHT_GEOGRAPHY;

            double finalScore = rawScore;
            double decayMultiplier = 1.0;

            String geographyReason = GeographyUtils.geographyReason(referenceCountry, c.country(), referenceWorkMode);
            List<String> reasonCodes = new ArrayList<>(buildReasonCodes(c.cosineSim(), matchedSkills, geoScore));
            if (referenceEmploymentType != null) {
                double employmentMatch = employmentCompatibility(c.employmentType(), referenceEmploymentType);
                if (employmentMatch > 0.5) {
                    reasonCodes.add("EMPLOYMENT_COMPATIBLE");
                }
            }

            results.add(new MatchResult(
                    c.nodeId(),
                    StringUtils.round3(finalScore),
                    c.displayName(),
                    c.avatarUrl(),
                    StringUtils.splitCommaSeparated(c.roles()),
                    c.seniority(),
                    c.skillsTechnical(),
                    c.toolsAndTech(),
                    c.skillsSoft(),
                    c.workMode(),
                    c.employmentType(),
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
                    c.linkedinUrl(),
                    new MatchScoreBreakdown(
                            StringUtils.round3(c.cosineSim()),
                            StringUtils.round3(skillsOverlap),
                            StringUtils.round3(geoScore),
                            StringUtils.round3(rawScore),
                            StringUtils.round3(decayMultiplier),
                            StringUtils.round3(finalScore),
                            matchedSkills,
                            geographyReason,
                            reasonCodes
                    )
            ));
        }

        results.sort(Comparator.comparingDouble(MatchResult::score).reversed());

        return results;
    }

    /**
     * Unified matches using a caller-supplied embedding (e.g. job vector search). Reference tags and
     * geography fields default to empty / null when not profiling a specific user node.
     */
    public List<MeshMatchResult> findAllMatches(
            UUID excludeUserId, float[] embedding,
            String typeFilter, String countryFilter) {
        return findAllMatches(excludeUserId, embedding, typeFilter, countryFilter, null, null);
    }

    public List<MeshMatchResult> findAllMatches(
            UUID excludeUserId, float[] embedding,
            String typeFilter, String countryFilter,
            Integer limit, Integer offset) {
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
                countryFilter,
                limit,
                offset);
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
            String typeFilter, String countryFilter,
            Integer limit, Integer offset) {
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
                                bd.embeddingScore(),
                                bd.skillsScore(),
                                0.0,
                                0.0,
                                0.0,
                                bd.geographyScore(),
                                bd.rawScore(),
                                bd.decayMultiplier(),
                                bd.finalScore(),
                                bd.matchedSkills() != null ? bd.matchedSkills() : Collections.emptyList(),
                                bd.geographyReason(),
                                bd.matchedSkills() != null ? bd.matchedSkills() : Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                bd.reasonCodes() != null ? bd.reasonCodes() : Collections.emptyList(),
                                referenceTags != null && !referenceTags.isEmpty(),
                                false,
                                false,
                                false,
                                referenceCountry != null && !referenceCountry.isBlank(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ) : null,
                        new MeshMatchResult.PersonDetails(
                                m.roles(),
                                m.seniority() != null ? m.seniority().name() : null,
                                m.skillsTechnical(), m.toolsAndTech(), m.skillsSoft(),
                                m.workMode() != null ? m.workMode().name() : null,
                                m.employmentType() != null ? m.employmentType().name() : null,
                                m.learningAreas(),
                                m.hobbies(), m.sports(), m.causes(),
                                m.city(),
                                m.timezone(),
                                m.slackHandle(),
                                m.email(),
                                m.telegramHandle(),
                                m.mobilePhone(),
                                m.linkedinUrl(),
                                null
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
                                bd.embeddingScore(),
                                bd.tagsScore(),
                                0.0,
                                0.0,
                                0.0,
                                bd.geographyScore(),
                                bd.rawScore(),
                                bd.decayMultiplier(),
                                bd.finalScore(),
                                bd.matchedTags() != null ? bd.matchedTags() : Collections.emptyList(),
                                bd.geographyReason(),
                                bd.matchedTags() != null ? bd.matchedTags() : Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                bd.reasonCodes() != null ? bd.reasonCodes() : Collections.emptyList(),
                                nodeReferenceTags != null && !nodeReferenceTags.isEmpty(),
                                false,
                                false,
                                false,
                                referenceCountry != null && !referenceCountry.isBlank(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ) : null,
                        null
                ));
            }
        }

        all.sort(Comparator.comparingDouble(MeshMatchResult::score).reversed());
        List<MeshMatchResult> result = SearchMatchingUtils.paginate(all, limit, offset, DEFAULT_RESULT_LIMIT);
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
        List<MeshNodeSearchRepository.NodeCandidateRow> rows = searchRepository
                .findNodeCandidatesByEmbedding(embedding, excludeUserId, targetType, poolSize);

        List<String> tagPool = myTags != null ? myTags : Collections.emptyList();

        List<NodeMatchResult> results = new ArrayList<>();
        for (MeshNodeSearchRepository.NodeCandidateRow row : rows) {
            String nodeCountry = row.country();
            if (country != null && (nodeCountry == null || !country.equalsIgnoreCase(nodeCountry))) {
                continue;
            }

            double cosineSim = row.cosineSim();
            List<String> nodeTags = row.tags();
            double tagsOverlap = MatchingUtils.jaccardSimilarity(tagPool, nodeTags);
            double geoScore = GeographyUtils.geographyScore(myCountry, nodeCountry, myWorkMode);

            double rawScore = cosineSim * WEIGHT_EMBEDDING + tagsOverlap * WEIGHT_SKILLS + geoScore * WEIGHT_GEOGRAPHY;

            double finalScore = rawScore;
            double decayMultiplier = 1.0;

            List<String> matchedTags = MatchingUtils.intersectCaseInsensitive(tagPool, nodeTags);
            String geoReason = GeographyUtils.geographyReason(myCountry, nodeCountry, myWorkMode);
            List<String> reasonCodes = new ArrayList<>();
            if (cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
            if (!matchedTags.isEmpty()) reasonCodes.add("TAGS_OVERLAP");
            if (geoScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");

            results.add(new NodeMatchResult(
                    row.nodeId(),
                    SqlParsingUtils.parseEnum(NodeType.class, row.nodeType()),
                    row.title(),
                    row.description(),
                    nodeTags,
                    nodeCountry,
                    StringUtils.round3(finalScore),
                    new NodeMatchBreakdown(
                            StringUtils.round3(cosineSim),
                            StringUtils.round3(tagsOverlap),
                            StringUtils.round3(geoScore),
                            StringUtils.round3(rawScore),
                            StringUtils.round3(decayMultiplier),
                            StringUtils.round3(finalScore),
                            matchedTags,
                            geoReason,
                            reasonCodes
                    )
            ));
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(NodeMatchResult::score).reversed())
                .toList();
    }

    double geographyScore(String myCountry, String theirCountry, WorkMode myWorkMode) {
        return GeographyUtils.geographyScore(myCountry, theirCountry, myWorkMode);
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

    private static List<String> buildReasonCodes(double cosineSim, List<String> matchedSkills,
                                                   double geoScore) {
        List<String> codes = new ArrayList<>();
        if (cosineSim >= 0.65) codes.add("SEMANTIC_SIMILARITY");
        if (!matchedSkills.isEmpty()) codes.add("SKILLS_OVERLAP");
        if (geoScore > 0) codes.add("LOCATION_COMPATIBLE");
        return codes;
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
            List<String> learningAreas,
            List<String> hobbies, List<String> sports, List<String> causes,
            String country, String city, String timezone,
            String slackHandle, String email, String telegramHandle, String mobilePhone,
            String linkedinUrl,
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
