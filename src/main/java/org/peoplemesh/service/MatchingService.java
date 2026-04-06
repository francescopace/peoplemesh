package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.JobMatchBreakdown;
import org.peoplemesh.domain.dto.JobMatchResult;
import org.peoplemesh.domain.dto.MatchFilters;
import org.peoplemesh.domain.dto.MatchResult;
import org.peoplemesh.domain.dto.MatchScoreBreakdown;
import org.peoplemesh.domain.enums.*;
import org.peoplemesh.domain.model.BlocklistEntry;
import org.peoplemesh.domain.model.JobPosting;
import org.peoplemesh.domain.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MatchingService {

    private static final double WEIGHT_EMBEDDING = 0.50;
    private static final double WEIGHT_SKILLS = 0.25;
    private static final double WEIGHT_GOALS = 0.15;
    private static final double WEIGHT_GEOGRAPHY = 0.10;

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

    @Inject
    EntityManager em;

    @Inject
    AppConfig config;

    @Inject
    AuditService audit;

    public List<MatchResult> findMatches(UUID userId, float[] queryEmbedding, MatchFilters filters) {
        if (queryEmbedding == null) {
            return Collections.emptyList();
        }

        UserProfile myProfile = UserProfile.findActiveByUserId(userId).orElse(null);
        if (myProfile == null) {
            return Collections.emptyList();
        }

        String vectorLiteral = vectorToString(queryEmbedding);
        int poolSize = config.matching().candidatePoolSize();

        @SuppressWarnings("unchecked")
        List<Object[]> candidates = em.createNativeQuery(
                        "SELECT p.id, p.user_id, p.seniority, p.skills_technical, p.skills_soft, " +
                        "p.tools_and_tech, p.work_mode, p.employment_type, p.collaboration_goals, " +
                        "p.topics_frequent, p.learning_areas, p.country, p.timezone, p.updated_at, " +
                        "p.show_country, " +
                        "(1 - (p.embedding <=> cast(:vec as vector))) as cosine_sim " +
                        "FROM profiles.user_profile p " +
                        "WHERE p.deleted_at IS NULL " +
                        "AND p.searchable = true " +
                        "AND p.embedding IS NOT NULL " +
                        "AND p.user_id != :userId " +
                        "ORDER BY p.embedding <=> cast(:vec as vector) " +
                        "LIMIT :poolSize")
                .setParameter("vec", vectorLiteral)
                .setParameter("userId", userId)
                .setParameter("poolSize", poolSize)
                .getResultList();

        List<MatchResult> results = new ArrayList<>();

        for (Object[] row : candidates) {
            UUID candidateUserId = (UUID) row[1];

            if (BlocklistEntry.isBlocked(userId, candidateUserId)) {
                continue;
            }

            double cosineSim = ((Number) row[15]).doubleValue();

            List<String> candidateSkills = parseArray(row[3]);
            List<String> candidateTools = parseArray(row[5]);
            double skillsOverlap = jaccardSimilarity(
                    combineLists(myProfile.skillsTechnical, myProfile.toolsAndTech),
                    combineLists(candidateSkills, candidateTools)
            );

            List<String> candidateGoals = parseArray(row[8]);
            List<CollaborationGoal> candidateGoalEnums = candidateGoals.stream()
                    .map(s -> parseEnum(CollaborationGoal.class, s))
                    .filter(Objects::nonNull)
                    .toList();
            double goalMatch = hasOverlap(myProfile.collaborationGoals, candidateGoalEnums) ? 1.0 : 0.0;

            WorkMode candidateWorkMode = parseEnum(WorkMode.class, (String) row[6]);
            EmploymentType candidateEmploymentType = parseEnum(EmploymentType.class, (String) row[7]);
            String candidateCountry = (String) row[11];
            double geoScore = geographyScore(myProfile.country, candidateCountry, myProfile.workMode);

            if (filters != null) {
                if (!passesFilters(filters, candidateSkills, candidateGoalEnums, candidateWorkMode,
                        candidateEmploymentType, candidateCountry)) {
                    continue;
                }
            }

            double rawScore = cosineSim * WEIGHT_EMBEDDING
                    + skillsOverlap * WEIGHT_SKILLS
                    + goalMatch * WEIGHT_GOALS
                    + geoScore * WEIGHT_GEOGRAPHY;

            Instant updatedAt = ((java.sql.Timestamp) row[13]).toInstant();
            double decayedScore = applyDecay(rawScore, updatedAt);
            double decayMultiplier = rawScore == 0.0 ? 1.0 : decayedScore / rawScore;

            UUID profileId = (UUID) row[0];
            boolean showCountry = (Boolean) row[14];
            List<String> matchedSkills = intersectCaseInsensitive(
                    combineLists(myProfile.skillsTechnical, myProfile.toolsAndTech),
                    combineLists(candidateSkills, candidateTools)
            );
            List<CollaborationGoal> matchedGoals = intersectEnums(myProfile.collaborationGoals, candidateGoalEnums);
            String geographyReason = geographyReason(myProfile.country, candidateCountry, myProfile.workMode);
            List<String> reasonCodes = new ArrayList<>();
            if (cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
            if (!matchedSkills.isEmpty()) reasonCodes.add("SKILLS_OVERLAP");
            if (!matchedGoals.isEmpty()) reasonCodes.add("GOALS_OVERLAP");
            if (geoScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
            if (decayMultiplier < 1.0) reasonCodes.add("RECENCY_DECAY_APPLIED");

            results.add(new MatchResult(
                    profileId,
                    Math.round(decayedScore * 1000.0) / 1000.0,
                    parseEnum(Seniority.class, (String) row[2]),
                    candidateSkills,
                    candidateTools,
                    parseArray(row[4]),
                    candidateWorkMode,
                    candidateEmploymentType,
                    candidateGoalEnums,
                    parseArray(row[9]),
                    parseArray(row[10]),
                    showCountry ? candidateCountry : null,
                    (String) row[12],
                    new MatchScoreBreakdown(
                            round3(cosineSim),
                            round3(skillsOverlap),
                            round3(goalMatch),
                            round3(geoScore),
                            round3(rawScore),
                            round3(decayMultiplier),
                            round3(decayedScore),
                            matchedSkills,
                            matchedGoals,
                            geographyReason,
                            reasonCodes
                    )
            ));
        }

        results.sort(Comparator.comparingDouble(MatchResult::score).reversed());

        audit.log(userId, "MATCHES_SEARCHED", "peoplemesh_find_matches");

        return results.stream()
                .limit(config.matching().resultLimit())
                .toList();
    }

    public List<JobMatchResult> findJobMatches(UUID userId, MatchFilters filters) {
        UserProfile myProfile = UserProfile.findActiveByUserId(userId).orElse(null);
        if (myProfile == null || myProfile.embedding == null) {
            return Collections.emptyList();
        }

        String vectorLiteral = vectorToString(myProfile.embedding);
        int poolSize = config.matching().candidatePoolSize();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT j.id, j.title, j.skills_required, j.work_mode, j.employment_type, " +
                        "j.country, j.updated_at, j.status, " +
                        "(1 - (j.embedding <=> cast(:vec as vector))) as cosine_sim " +
                        "FROM jobs.job_posting j " +
                        "WHERE j.status = 'PUBLISHED' " +
                        "AND j.embedding IS NOT NULL " +
                        "ORDER BY j.embedding <=> cast(:vec as vector) " +
                        "LIMIT :poolSize")
                .setParameter("vec", vectorLiteral)
                .setParameter("poolSize", poolSize)
                .getResultList();

        List<JobMatchResult> results = new ArrayList<>();
        List<String> mySkills = combineLists(myProfile.skillsTechnical, myProfile.toolsAndTech);
        for (Object[] row : rows) {
            List<String> jobSkills = parseArray(row[2]);
            WorkMode jobWorkMode = parseEnum(WorkMode.class, (String) row[3]);
            EmploymentType jobEmploymentType = parseEnum(EmploymentType.class, (String) row[4]);
            String jobCountry = (String) row[5];

            if (filters != null && !passesFilters(filters, jobSkills, Collections.emptyList(),
                    jobWorkMode, jobEmploymentType, jobCountry)) {
                continue;
            }

            double cosineSim = ((Number) row[8]).doubleValue();
            double skillsOverlap = jaccardSimilarity(mySkills, jobSkills);
            double employmentScore = employmentCompatibility(myProfile.employmentType, jobEmploymentType);
            double geoScore = geographyScore(myProfile.country, jobCountry, myProfile.workMode);
            double rawScore = cosineSim * WEIGHT_EMBEDDING
                    + skillsOverlap * WEIGHT_SKILLS
                    + employmentScore * WEIGHT_GOALS
                    + geoScore * WEIGHT_GEOGRAPHY;

            Instant updatedAt = ((java.sql.Timestamp) row[6]).toInstant();
            double decayedScore = applyDecay(rawScore, updatedAt);
            double decayMultiplier = rawScore == 0.0 ? 1.0 : decayedScore / rawScore;

            List<String> matchedSkills = intersectCaseInsensitive(mySkills, jobSkills);
            List<String> reasonCodes = new ArrayList<>();
            if (cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
            if (!matchedSkills.isEmpty()) reasonCodes.add("SKILLS_OVERLAP");
            if (employmentScore > 0.5) reasonCodes.add("EMPLOYMENT_COMPATIBLE");
            if (geoScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
            if (decayMultiplier < 1.0) reasonCodes.add("RECENCY_DECAY_APPLIED");

            results.add(new JobMatchResult(
                    (UUID) row[0],
                    (String) row[1],
                    parseEnum(JobStatus.class, (String) row[7]),
                    jobWorkMode,
                    jobEmploymentType,
                    jobCountry,
                    jobSkills,
                    round3(decayedScore),
                    new JobMatchBreakdown(
                            round3(cosineSim),
                            round3(skillsOverlap),
                            round3(employmentScore),
                            round3(geoScore),
                            round3(rawScore),
                            round3(decayMultiplier),
                            round3(decayedScore),
                            matchedSkills,
                            geographyReason(myProfile.country, jobCountry, myProfile.workMode),
                            reasonCodes
                    )
            ));
        }

        audit.log(userId, "JOBS_MATCHED", "peoplemesh_find_job_matches");
        return results.stream()
                .sorted(Comparator.comparingDouble(JobMatchResult::score).reversed())
                .limit(config.matching().resultLimit())
                .toList();
    }

    public List<MatchResult> findCandidatesForJob(UUID ownerUserId, UUID jobId, MatchFilters filters) {
        JobPosting job = JobPosting.findByIdAndOwner(jobId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (job.embedding == null) {
            return Collections.emptyList();
        }

        String vectorLiteral = vectorToString(job.embedding);
        int poolSize = config.matching().candidatePoolSize();

        @SuppressWarnings("unchecked")
        List<Object[]> candidates = em.createNativeQuery(
                        "SELECT p.id, p.user_id, p.seniority, p.skills_technical, p.skills_soft, " +
                        "p.tools_and_tech, p.work_mode, p.employment_type, p.collaboration_goals, " +
                        "p.topics_frequent, p.learning_areas, p.country, p.timezone, p.updated_at, " +
                        "p.show_country, " +
                        "(1 - (p.embedding <=> cast(:vec as vector))) as cosine_sim " +
                        "FROM profiles.user_profile p " +
                        "WHERE p.deleted_at IS NULL " +
                        "AND p.searchable = true " +
                        "AND p.embedding IS NOT NULL " +
                        "AND p.user_id != :ownerUserId " +
                        "ORDER BY p.embedding <=> cast(:vec as vector) " +
                        "LIMIT :poolSize")
                .setParameter("vec", vectorLiteral)
                .setParameter("ownerUserId", ownerUserId)
                .setParameter("poolSize", poolSize)
                .getResultList();

        List<MatchResult> results = new ArrayList<>();
        for (Object[] row : candidates) {
            UUID candidateUserId = (UUID) row[1];
            if (BlocklistEntry.isBlocked(ownerUserId, candidateUserId)) {
                continue;
            }

            List<String> candidateSkills = parseArray(row[3]);
            List<String> candidateTools = parseArray(row[5]);
            List<String> candidateSkillPool = combineLists(candidateSkills, candidateTools);
            List<String> jobSkillPool = job.skillsRequired == null ? Collections.emptyList() : job.skillsRequired;
            List<String> candidateGoals = parseArray(row[8]);
            List<CollaborationGoal> candidateGoalEnums = candidateGoals.stream()
                    .map(s -> parseEnum(CollaborationGoal.class, s))
                    .filter(Objects::nonNull)
                    .toList();
            WorkMode candidateWorkMode = parseEnum(WorkMode.class, (String) row[6]);
            EmploymentType candidateEmploymentType = parseEnum(EmploymentType.class, (String) row[7]);
            String candidateCountry = (String) row[11];

            if (filters != null && !passesFilters(filters, candidateSkills, candidateGoalEnums,
                    candidateWorkMode, candidateEmploymentType, candidateCountry)) {
                continue;
            }

            double cosineSim = ((Number) row[15]).doubleValue();
            double skillsOverlap = jaccardSimilarity(jobSkillPool, candidateSkillPool);
            double employmentMatch = employmentCompatibility(candidateEmploymentType, job.employmentType);
            double geoScore = geographyScore(job.country, candidateCountry, job.workMode);
            double rawScore = cosineSim * WEIGHT_EMBEDDING
                    + skillsOverlap * WEIGHT_SKILLS
                    + employmentMatch * WEIGHT_GOALS
                    + geoScore * WEIGHT_GEOGRAPHY;

            Instant updatedAt = ((java.sql.Timestamp) row[13]).toInstant();
            double decayedScore = applyDecay(rawScore, updatedAt);
            double decayMultiplier = rawScore == 0.0 ? 1.0 : decayedScore / rawScore;
            boolean showCountry = (Boolean) row[14];
            List<String> matchedSkills = intersectCaseInsensitive(jobSkillPool, candidateSkillPool);
            List<String> reasonCodes = new ArrayList<>();
            if (cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
            if (!matchedSkills.isEmpty()) reasonCodes.add("SKILLS_OVERLAP");
            if (employmentMatch > 0.5) reasonCodes.add("EMPLOYMENT_COMPATIBLE");
            if (geoScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
            if (decayMultiplier < 1.0) reasonCodes.add("RECENCY_DECAY_APPLIED");

            results.add(new MatchResult(
                    (UUID) row[0],
                    round3(decayedScore),
                    parseEnum(Seniority.class, (String) row[2]),
                    candidateSkills,
                    candidateTools,
                    parseArray(row[4]),
                    candidateWorkMode,
                    candidateEmploymentType,
                    candidateGoalEnums,
                    parseArray(row[9]),
                    parseArray(row[10]),
                    showCountry ? candidateCountry : null,
                    (String) row[12],
                    new MatchScoreBreakdown(
                            round3(cosineSim),
                            round3(skillsOverlap),
                            round3(employmentMatch),
                            round3(geoScore),
                            round3(rawScore),
                            round3(decayMultiplier),
                            round3(decayedScore),
                            matchedSkills,
                            Collections.emptyList(),
                            geographyReason(job.country, candidateCountry, job.workMode),
                            reasonCodes
                    )
            ));
        }

        audit.log(ownerUserId, "CANDIDATES_MATCHED_FOR_JOB", "job_find_candidates");
        return results.stream()
                .sorted(Comparator.comparingDouble(MatchResult::score).reversed())
                .limit(config.matching().resultLimit())
                .toList();
    }

    double jaccardSimilarity(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> setA = a.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> setB = b.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    double geographyScore(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return 1.0;
        if (myCountry == null || theirCountry == null) return 0.0;
        if (myCountry.equalsIgnoreCase(theirCountry)) return 1.0;
        if (sameContinent(myCountry, theirCountry)) return 0.5;
        return 0.0;
    }

    double applyDecay(double score, Instant updatedAt) {
        long monthsOld = Duration.between(updatedAt, Instant.now()).toDays() / 30;
        if (monthsOld <= 6) return score;
        return score * Math.exp(-config.matching().decayLambda() * (monthsOld - 6));
    }

    private <T> boolean hasOverlap(List<T> a, List<T> b) {
        if (a == null || b == null) return false;
        return a.stream().anyMatch(b::contains);
    }

    private double employmentCompatibility(EmploymentType candidate, EmploymentType required) {
        if (candidate == null || required == null) return 0.5;
        if (candidate == EmploymentType.OPEN_TO_OFFERS || candidate == EmploymentType.LOOKING) return 1.0;
        return candidate == required ? 1.0 : 0.0;
    }

    private boolean passesFilters(
            MatchFilters filters,
            List<String> skillsTechnical,
            List<CollaborationGoal> collaborationGoals,
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
            if (intersectCaseInsensitive(filters.skillsTechnical(), skillsTechnical).isEmpty()) {
                return false;
            }
        }
        if (filters.collaborationGoals() != null && !filters.collaborationGoals().isEmpty()) {
            if (intersectEnums(filters.collaborationGoals(), collaborationGoals).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private <T> List<T> intersectEnums(List<T> a, List<T> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return Collections.emptyList();
        }
        Set<T> set = new HashSet<>(b);
        return a.stream().filter(set::contains).distinct().toList();
    }

    private List<String> intersectCaseInsensitive(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> bLower = b.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return a.stream()
                .filter(Objects::nonNull)
                .filter(s -> bLower.contains(s.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private String geographyReason(String myCountry, String theirCountry, WorkMode myWorkMode) {
        if (myWorkMode == WorkMode.REMOTE) return "remote_friendly";
        if (myCountry == null || theirCountry == null) return "location_unknown";
        if (myCountry.equalsIgnoreCase(theirCountry)) return "same_country";
        if (sameContinent(myCountry, theirCountry)) return "same_continent";
        return "different_region";
    }

    private List<String> combineLists(List<String> a, List<String> b) {
        List<String> combined = new ArrayList<>();
        if (a != null) combined.addAll(a);
        if (b != null) combined.addAll(b);
        return combined;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseArray(Object obj) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof String[] arr) return List.of(arr);
        if (obj instanceof java.sql.Array sqlArray) {
            try {
                Object value = sqlArray.getArray();
                if (value instanceof String[] arr) {
                    return List.of(arr);
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        if (obj instanceof List) return (List<String>) obj;
        return Collections.emptyList();
    }

    private String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private boolean sameContinent(String a, String b) {
        String contA = CONTINENT_MAP.getOrDefault(a.toUpperCase(), "XX");
        String contB = CONTINENT_MAP.getOrDefault(b.toUpperCase(), "YY");
        return contA.equals(contB);
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
