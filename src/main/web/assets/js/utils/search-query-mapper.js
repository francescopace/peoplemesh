const ISO_COUNTRIES = new Set([
  "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ",
  "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS",
  "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN",
  "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ", "DE", "DJ", "DK", "DM", "DO", "DZ", "EC", "EE",
  "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF",
  "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM",
  "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM",
  "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC",
  "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK",
  "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA",
  "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG",
  "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW",
  "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS",
  "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO",
  "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE", "VG", "VI",
  "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW",
]);

const COUNTRY_ALIASES = {
  italy: "IT",
  italia: "IT",
  germany: "DE",
  deutschland: "DE",
  france: "FR",
  spain: "ES",
  spagna: "ES",
  portugal: "PT",
  portogallo: "PT",
  "united states": "US",
  usa: "US",
  uk: "GB",
  "united kingdom": "GB",
  england: "GB",
  ireland: "IE",
  netherlands: "NL",
  "paesi bassi": "NL",
  sweden: "SE",
  norway: "NO",
  denmark: "DK",
  finland: "FI",
  poland: "PL",
  austria: "AT",
  switzerland: "CH",
  brazil: "BR",
  india: "IN",
  japan: "JP",
  canada: "CA",
  mexico: "MX",
  romania: "RO",
  turkey: "TR",
  china: "CN",
  korea: "KR",
};

const LOCATION_WORDS_TO_IGNORE = new Set([
  "europe", "eu", "european union",
  "asia", "africa", "north america", "south america", "oceania",
  "worldwide", "global", "remote",
]);

export function inferAutoTypeFromParsedQuery(parsedQuery) {
  if (!parsedQuery) return "";
  const scope = String(parsedQuery.result_scope || "").trim().toLowerCase();
  if (scope && scope !== "unknown") {
    const scopeToTab = {
      all: "",
      people: "profile",
      jobs: "JOB",
      communities: "COMMUNITY",
      events: "EVENT",
      projects: "PROJECT",
      groups: "INTEREST_GROUP",
    };
    if (scope in scopeToTab) {
      return scopeToTab[scope];
    }
  }
  const mustHaveRoles = parsedQuery.must_have?.roles || [];
  const keywords = (parsedQuery.keywords || []).map((k) => String(k || "").toLowerCase());
  const text = `${parsedQuery.embedding_text || ""} ${(parsedQuery.keywords || []).join(" ")}`.toLowerCase();

  if (keywords.some((k) => k.includes("community")) || text.includes("community")) return "COMMUNITY";
  if (keywords.some((k) => k.includes("event")) || text.includes("event") || text.includes("meetup")) return "EVENT";
  if (keywords.some((k) => k.includes("job")) || text.includes("open role") || text.includes("opportunit")) return "JOB";
  if (keywords.some((k) => k.includes("project")) || text.includes("project")) return "PROJECT";
  if (keywords.some((k) => k.includes("group")) || text.includes("group")) return "INTEREST_GROUP";
  if (mustHaveRoles.length > 0 || (parsedQuery.must_have?.skills || []).length > 0) return "profile";
  return "";
}

export function buildProfileSchemaFromParsedQuery(parsedQuery) {
  if (!parsedQuery) return null;
  const mustHave = parsedQuery.must_have || {};
  const niceToHave = parsedQuery.nice_to_have || {};

  const roles = dedupeList(mustHave.roles || []);
  const technicalSkills = dedupeList([...(mustHave.skills || []), ...(niceToHave.skills || [])]);
  const languages = dedupeList(mustHave.languages || []);
  const topics = dedupeList(parsedQuery.keywords || []);
  const country = pickCountryFromLocations(mustHave.location || []);
  const seniority = mapSeniority(parsedQuery.seniority);

  const professional = hasAny([roles, technicalSkills, languages, seniority])
    ? {
        roles: roles.length ? roles : undefined,
        seniority: seniority || undefined,
        skills_technical: technicalSkills.length ? technicalSkills : undefined,
        languages_spoken: languages.length ? languages : undefined,
      }
    : undefined;

  const interestsProfessional = topics.length
    ? { topics_frequent: topics }
    : undefined;

  const geography = country ? { country } : undefined;

  return {
    profile_version: "search-derived-v1",
    generated_at: new Date().toISOString(),
    professional,
    interests_professional: interestsProfessional,
    geography,
  };
}

export function toMatchesTypeFilter(searchTabType) {
  if (!searchTabType) return "";
  if (searchTabType === "profile") return "PEOPLE";
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
  const matchedMust = highlight.must.length ? highlight.must : commonItems;
  const matchedNice = highlight.nice;
  const breakdown = {
    matchedMustHaveSkills: matchedMust,
    matchedNiceToHaveSkills: matchedNice,
    missingMustHaveSkills: [],
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

function mapSeniority(raw) {
  if (!raw) return null;
  const normalized = String(raw).trim().toLowerCase();
  if (normalized === "junior") return "JUNIOR";
  if (normalized === "mid") return "MID";
  if (normalized === "senior") return "SENIOR";
  if (normalized === "lead") return "LEAD";
  return null;
}

function pickCountryFromLocations(locations) {
  for (const value of locations || []) {
    const code = mapLocationToCountryCode(value);
    if (code) return code;
  }
  return undefined;
}

function mapLocationToCountryCode(raw) {
  if (!raw) return null;
  const trimmed = String(raw).trim();
  if (!trimmed) return null;
  if (trimmed.length === 2) {
    const code = trimmed.toUpperCase();
    if (ISO_COUNTRIES.has(code)) return code;
  }
  const normalized = trimmed.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim().replace(/\s+/g, " ");
  if (!normalized || LOCATION_WORDS_TO_IGNORE.has(normalized)) return null;
  return COUNTRY_ALIASES[normalized] || null;
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

function hasAny(values) {
  return values.some((v) => (Array.isArray(v) ? v.length > 0 : Boolean(v)));
}

function buildHighlightTerms(parsedQuery) {
  const mustSkills = dedupeList(parsedQuery?.must_have?.skills || []);
  const niceSkills = dedupeList(parsedQuery?.nice_to_have?.skills || []);
  return {
    must: mustSkills,
    nice: niceSkills,
  };
}
