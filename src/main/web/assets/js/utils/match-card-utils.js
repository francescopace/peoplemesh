import { el } from "../ui.js";
import { termsMatch } from "./term-matching.js";

export const CARD_BADGE_LIMITS = {
  search: {
    profileSkills: 14,
    nodeTags: 12,
  },
  myMesh: {
    tags: 8,
  },
};

const DEFAULT_TOOLTIP_WEIGHTS = {
  semantic: 0.70,
  mustHave: 0.20,
  niceToHave: 0.30,
  language: 0.10,
  geography: 0.20,
  industry: 0.10,
  seniority: 0.05,
  keyword: 0.10,
};

const POINT_VISIBILITY_EPSILON = 0.04;

export function buildScoreWithTooltip(scoreValue, colors, breakdown) {
  const scoreWrap = el("div", { className: "dc-score-wrap" });
  scoreWrap.appendChild(el("div", { className: "dc-score", style: `color:${colors.color}` }, `${Math.round((scoreValue || 0) * 100)}%`));

  const tooltip = buildWhyMatchTooltip(scoreValue, breakdown);
  if (!tooltip) return scoreWrap;

  const infoButton = el(
    "button",
    {
      className: "dc-match-info-btn",
      type: "button",
      "aria-label": "Show why this match scored this way",
    },
    el("span", { className: "material-symbols-outlined icon-16" }, "info"),
  );
  infoButton.appendChild(tooltip);
  scoreWrap.appendChild(infoButton);
  return scoreWrap;
}

export function sortTermsByMatchPriority(terms, matchedTerms) {
  const sourceTerms = terms || [];
  const matches = sourceTerms.filter((term) => (matchedTerms || []).some((matched) => termsMatch(matched, term)));
  const nonMatches = sourceTerms.filter((term) => !(matchedTerms || []).some((matched) => termsMatch(matched, term)));
  return [...matches, ...nonMatches];
}

export function findSkillLevel(skillLevels, skillName) {
  if (!skillLevels) return 0;
  const direct = skillLevels[skillName];
  if (direct != null) return direct;
  const normalized = (skillName || "").toLowerCase();
  for (const [name, level] of Object.entries(skillLevels)) {
    if (name.toLowerCase() === normalized) return level;
  }
  return 0;
}

function buildWhyMatchTooltip(scoreValue, breakdown) {
  if (!breakdown) return null;

  const mustHaveCoverage = breakdown.mustHaveSkillCoverage ?? breakdown.overlapScore;
  const niceToHaveScore = breakdown.niceToHaveBonus ?? breakdown.niceToHaveScore;

  const tooltip = el("div", { className: "dc-match-tooltip", role: "tooltip" });
  tooltip.appendChild(el("div", { className: "dc-match-tooltip-title" }, "Score points"));

  const scoreLines = el("div", { className: "dc-match-tooltip-lines" });
  const weights = resolveTooltipWeights(breakdown);

  const semanticPoints = toPoints(breakdown.embeddingScore, weights.semantic);
  const mustHavePoints = breakdown.mustHaveRequested === false ? 0 : toPoints(mustHaveCoverage, weights.mustHave);
  const niceToHavePoints = breakdown.niceToHaveRequested === false ? 0 : toPoints(niceToHaveScore, weights.niceToHave);
  const languagePoints = breakdown.languageRequested === false ? 0 : toPoints(breakdown.languageScore, weights.language);
  const geographyPoints = breakdown.geographyRequested === false ? 0 : toPoints(breakdown.geographyScore, weights.geography);
  const industryPoints = breakdown.industryRequested === false ? 0 : toPoints(breakdown.industryScore, weights.industry);
  const seniorityPoints = toPoints(breakdown.seniorityScore, weights.seniority);
  const keywordPoints = toPoints(breakdown.keywordScore, weights.keyword);

  appendPointsLine(scoreLines, "Semantic similarity", semanticPoints);
  appendPointsLine(scoreLines, "Must-have skills", mustHavePoints);
  appendPointsLine(scoreLines, "Nice-to-have skills", niceToHavePoints);
  appendPointsLine(scoreLines, "Keywords", keywordPoints);
  appendPointsLine(scoreLines, "Language", languagePoints);
  appendPointsLine(scoreLines, "Geography", geographyPoints);
  appendPointsLine(scoreLines, "Industry", industryPoints);
  appendPointsLine(scoreLines, "Seniority", seniorityPoints);

  const basePoints = semanticPoints
    + mustHavePoints
    + niceToHavePoints
    + keywordPoints
    + languagePoints
    + geographyPoints
    + industryPoints
    + seniorityPoints;

  const mustPenaltyFactor = normalizeFactor(breakdown.mustHavePenaltyFactor);
  const negativePenaltyFactor = normalizeFactor(breakdown.negativeSkillsPenaltyFactor);
  const afterMustPenalty = basePoints * mustPenaltyFactor;
  const mustPenaltyPoints = afterMustPenalty - basePoints;
  appendPointsLine(scoreLines, "Must-have missing malus", mustPenaltyPoints);
  const afterNegativePenalty = afterMustPenalty * negativePenaltyFactor;
  const negativePenaltyPoints = afterNegativePenalty - afterMustPenalty;
  appendPointsLine(scoreLines, "Negative skills malus", negativePenaltyPoints);

  const finalScorePct = resolveFinalScorePercent(scoreValue, breakdown.finalScore);
  const residualPoints = finalScorePct - afterNegativePenalty;
  appendPointsLine(scoreLines, "Other scoring factors", residualPoints);
  const totalPoints = afterNegativePenalty + residualPoints;
  scoreLines.appendChild(el("div", {}, `Total points: ${formatSignedPoints(totalPoints)}`));
  tooltip.appendChild(scoreLines);
  return tooltip;
}

function toPoints(value, weight) {
  if (typeof value !== "number" || typeof weight !== "number") {
    return 0;
  }
  return value * weight * 100;
}

function normalizeFactor(value) {
  return typeof value === "number" ? value : 1.0;
}

function resolveTooltipWeights(breakdown) {
  return {
    semantic: pickWeight(breakdown.weightEmbedding, DEFAULT_TOOLTIP_WEIGHTS.semantic),
    mustHave: pickWeight(breakdown.weightMustHave, DEFAULT_TOOLTIP_WEIGHTS.mustHave),
    niceToHave: pickWeight(breakdown.weightNiceToHave, DEFAULT_TOOLTIP_WEIGHTS.niceToHave),
    language: pickWeight(breakdown.weightLanguage, DEFAULT_TOOLTIP_WEIGHTS.language),
    geography: pickWeight(breakdown.weightGeography, DEFAULT_TOOLTIP_WEIGHTS.geography),
    industry: pickWeight(breakdown.weightIndustry, DEFAULT_TOOLTIP_WEIGHTS.industry),
    seniority: pickWeight(breakdown.weightSeniority, DEFAULT_TOOLTIP_WEIGHTS.seniority),
    keyword: pickWeight(breakdown.weightKeyword, DEFAULT_TOOLTIP_WEIGHTS.keyword),
  };
}

function pickWeight(value, fallback) {
  return typeof value === "number" ? value : fallback;
}

function appendPointsLine(parent, label, points) {
  if (Math.abs(points) <= POINT_VISIBILITY_EPSILON) {
    return;
  }
  parent.appendChild(el("div", {}, `${label}: ${formatSignedPoints(points)}`));
}

function formatSignedPoints(points, includePtSuffix = true) {
  const formatted = formatPoints(points);
  return `${points > 0 ? "+" : ""}${formatted}${includePtSuffix ? " pt" : ""}`;
}

function formatPoints(points, oneDecimal = true) {
  if (typeof points !== "number") {
    return oneDecimal ? "0.0" : "0";
  }
  return oneDecimal ? points.toFixed(1) : String(Math.round(points));
}

function toPercent(value) {
  if (typeof value !== "number") {
    return 0;
  }
  return value * 100;
}

function resolveFinalScorePercent(scoreValue, finalScore) {
  if (typeof finalScore === "number" && finalScore > 0) {
    return toPercent(finalScore);
  }
  return toPercent(scoreValue);
}
