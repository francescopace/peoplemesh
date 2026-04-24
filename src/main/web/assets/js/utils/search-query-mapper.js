export function inferAutoTypeFromParsedQuery(parsedQuery) {
  if (!parsedQuery) return "";
  const scope = String(parsedQuery.result_scope || "").trim().toLowerCase();
  if (!scope || scope === "unknown") {
    return "";
  }
  const scopeToTab = {
    all: "",
    people: "PEOPLE",
    jobs: "JOB",
    communities: "COMMUNITY",
    events: "EVENT",
    projects: "PROJECT",
    groups: "INTEREST_GROUP",
  };
  return scopeToTab[scope] || "";
}

export function toMatchesTypeFilter(searchTabType) {
  if (!searchTabType) return "";
  return searchTabType;
}

export function adaptMatchesToSearchResponse(matches, parsedQuery = null) {
  const source = Array.isArray(matches) ? matches : [];
  const highlight = buildHighlightTerms(parsedQuery);
  return {
    parsedQuery: null,
    results: source.map((item) => mapSingleMatch(item, highlight)),
  };
}

function mapSingleMatch(item, highlight) {
  const nodeType = item?.nodeType || "UNKNOWN";
  const isPeople = nodeType === "PEOPLE";
  const commonItems = item?.breakdown?.commonItems || [];
  const matchedMust = item?.breakdown?.matchedMustHaveSkills
    || (highlight.must.length ? highlight.must : commonItems);
  const matchedNice = item?.breakdown?.matchedNiceToHaveSkills
    || highlight.nice;
  const breakdown = {
    embeddingScore: Number(item?.breakdown?.embeddingScore || 0),
    mustHaveSkillCoverage: Number(item?.breakdown?.mustHaveSkillCoverage ?? item?.breakdown?.overlapScore ?? 0),
    niceToHaveBonus: Number(item?.breakdown?.niceToHaveBonus ?? item?.breakdown?.niceToHaveScore ?? 0),
    languageScore: Number(item?.breakdown?.languageScore || 0),
    industryScore: Number(item?.breakdown?.industryScore || 0),
    geographyScore: Number(item?.breakdown?.geographyScore || 0),
    finalScore: Number(item?.breakdown?.finalScore || 0),
    matchedMustHaveSkills: matchedMust,
    matchedNiceToHaveSkills: matchedNice,
    missingMustHaveSkills: item?.breakdown?.missingMustHaveSkills || [],
    reasonCodes: item?.breakdown?.reasonCodes || [],
    geographyReason: item?.breakdown?.geographyReason || null,
    mustHaveRequested: item?.breakdown?.mustHaveRequested,
    niceToHaveRequested: item?.breakdown?.niceToHaveRequested,
    languageRequested: item?.breakdown?.languageRequested,
    industryRequested: item?.breakdown?.industryRequested,
    geographyRequested: item?.breakdown?.geographyRequested,
    mustHavePenaltyFactor: item?.breakdown?.mustHavePenaltyFactor,
    negativeSkillsPenaltyFactor: item?.breakdown?.negativeSkillsPenaltyFactor,
    seniorityScore: item?.breakdown?.seniorityScore,
    keywordScore: item?.breakdown?.keywordScore,
    weightEmbedding: item?.breakdown?.weightEmbedding,
    weightMustHave: item?.breakdown?.weightMustHave,
    weightNiceToHave: item?.breakdown?.weightNiceToHave,
    weightLanguage: item?.breakdown?.weightLanguage,
    weightIndustry: item?.breakdown?.weightIndustry,
    weightGeography: item?.breakdown?.weightGeography,
    weightSeniority: item?.breakdown?.weightSeniority,
    weightKeyword: item?.breakdown?.weightKeyword,
  };
  if (isPeople) {
    return {
      id: item.id,
      resultType: "profile",
      score: item.score ?? 0,
      displayName: item.title || "Anonymous",
      avatarUrl: item.avatarUrl || null,
      roles: item.person?.roles || [],
      seniority: item.person?.seniority || null,
      skillsTechnical: item.person?.skillsTechnical || [],
      toolsAndTech: item.person?.toolsAndTech || [],
      languagesSpoken: [],
      country: item.country || null,
      city: item.person?.city || null,
      workMode: item.person?.workMode || null,
      employmentType: item.person?.employmentType || null,
      slackHandle: item.person?.slackHandle || null,
      email: item.person?.email || null,
      telegramHandle: item.person?.telegramHandle || null,
      mobilePhone: item.person?.mobilePhone || null,
      breakdown,
      skill_levels: {},
    };
  }
  return {
    id: item.id,
    resultType: "node",
    score: item.score ?? 0,
    nodeType,
    title: item.title || "Untitled",
    description: item.description || "",
    tags: item.tags || [],
    country: item.country || null,
    breakdown,
  };
}

function dedupeList(values) {
  const seen = new Set();
  const out = [];
  for (const value of values || []) {
    const normalized = String(value || "").trim();
    if (!normalized) continue;
    const key = normalized.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(normalized);
  }
  return out;
}

function buildHighlightTerms(parsedQuery) {
  const mustSkills = dedupeList(parsedQuery?.must_have?.skills || []);
  const niceSkills = dedupeList(parsedQuery?.nice_to_have?.skills || []);
  return {
    must: mustSkills,
    nice: niceSkills,
  };
}
