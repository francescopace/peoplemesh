const IMPORT_FIELD_DEFS = [
  ["Identity", "Birth Date", "identity.birth_date", "identity", "birth_date", "identity.birth_date"],
  ["Professional", "Roles", "professional.roles", "professional", "roles", "professional.roles"],
  ["Professional", "Seniority", "professional.seniority", "professional", "seniority", "professional.seniority"],
  ["Professional", "Technical Skills", "professional.skills_technical", "professional", "skills_technical", "professional.skills_technical"],
  ["Professional", "Soft Skills", "professional.skills_soft", "professional", "skills_soft", "professional.skills_soft"],
  ["Professional", "Tools & Tech", "professional.tools_and_tech", "professional", "tools_and_tech", "professional.tools_and_tech"],
  ["Professional", "Languages Spoken", "professional.languages_spoken", "professional", "languages_spoken", "professional.languages_spoken"],
  ["Professional", "Industries", "professional.industries", "professional", "industries", "professional.industries"],
  ["Professional", "Work Mode", "professional.work_mode_preference", "professional", "work_mode_preference", "professional.work_mode_preference"],
  ["Professional", "Employment Type", "professional.employment_type", "professional", "employment_type", "professional.employment_type"],
  ["Contacts", "Slack", "contacts.slack_handle", "contacts", "slack_handle", "contacts.slack_handle"],
  ["Contacts", "Telegram", "contacts.telegram_handle", "contacts", "telegram_handle", "contacts.telegram_handle"],
  ["Contacts", "Mobile", "contacts.mobile_phone", "contacts", "mobile_phone", "contacts.mobile_phone"],
  ["Contacts", "LinkedIn", "contacts.linkedin_url", "contacts", "linkedin_url", "contacts.linkedin_url"],
  ["Interests", "Learning Areas", "interests.learning_areas", "interests_professional", "learning_areas", "interests_professional.learning_areas"],
  ["Interests", "Project Types", "interests.project_types", "interests_professional", "project_types", "interests_professional.project_types"],
  ["Personal", "Hobbies", "personal.hobbies", "personal", "hobbies", "personal.hobbies"],
  ["Personal", "Sports", "personal.sports", "personal", "sports", "personal.sports"],
  ["Personal", "Education", "personal.education", "personal", "education", "personal.education"],
  ["Personal", "Causes", "personal.causes", "personal", "causes", "personal.causes"],
  ["Personal", "Personality Tags", "personal.personality_tags", "personal", "personality_tags", "personal.personality_tags"],
  ["Personal", "Music Genres", "personal.music_genres", "personal", "music_genres", "personal.music_genres"],
  ["Personal", "Book Genres", "personal.book_genres", "personal", "book_genres", "personal.book_genres"],
  ["Location", "Country", "geography.country", "geography", "country", "geography.country"],
  ["Location", "City", "geography.city", "geography", "city", "geography.city"],
  ["Location", "Timezone", "geography.timezone", "geography", "timezone", "geography.timezone"],
];

const MERGEABLE_IMPORT_KEYS = new Set([
  "professional.skills_technical",
  "professional.skills_soft",
  "professional.tools_and_tech",
  "professional.languages_spoken",
  "professional.industries",
  "interests.learning_areas",
  "interests.project_types",
  "personal.hobbies",
  "personal.sports",
  "personal.education",
  "personal.causes",
  "personal.personality_tags",
  "personal.music_genres",
  "personal.book_genres",
]);

const LIST_FIELD_LIMITS = new Map([
  ["professional.roles", { maxItems: 20, maxItemLength: 200 }],
  ["professional.industries", { maxItems: 20, maxItemLength: 200 }],
  ["professional.skills_technical", { maxItems: 50, maxItemLength: 100 }],
  ["professional.skills_soft", { maxItems: 30, maxItemLength: 100 }],
  ["professional.tools_and_tech", { maxItems: 50, maxItemLength: 100 }],
  ["professional.languages_spoken", { maxItems: 30, maxItemLength: 50 }],
  ["interests.learning_areas", { maxItems: 50, maxItemLength: 200 }],
  ["interests.project_types", { maxItems: 20, maxItemLength: 200 }],
  ["personal.hobbies", { maxItems: 30, maxItemLength: 100 }],
  ["personal.sports", { maxItems: 20, maxItemLength: 100 }],
  ["personal.education", { maxItems: 20, maxItemLength: 200 }],
  ["personal.causes", { maxItems: 20, maxItemLength: 200 }],
  ["personal.personality_tags", { maxItems: 20, maxItemLength: 100 }],
  ["personal.music_genres", { maxItems: 20, maxItemLength: 100 }],
  ["personal.book_genres", { maxItems: 20, maxItemLength: 100 }],
]);

function displayVal(value) {
  if (value == null) return "\u2014";
  if (Array.isArray(value)) return value.length ? value.join(", ") : "\u2014";
  let text = String(value).trim();
  if (!text) return "\u2014";
  if (text.startsWith("http") && text.length > 60) {
    try {
      text = `${new URL(text).hostname}/\u2026`;
    } catch {
      text = `${text.slice(0, 55)}\u2026`;
    }
  }
  return text;
}

function normalizeMergeValue(value) {
  if (value == null) return null;
  const normalized = String(value).trim();
  return normalized || null;
}

function hasArrayValues(value) {
  return Array.isArray(value) && value.some((item) => normalizeMergeValue(item) != null);
}

function mergeDistinctValues(currentValues, importedValues, uiKey) {
  const limits = LIST_FIELD_LIMITS.get(uiKey);
  const merged = [];
  const seen = new Set();
  for (const list of [currentValues, importedValues]) {
    if (!Array.isArray(list)) continue;
    for (const item of list) {
      const normalized = normalizeMergeValue(item);
      if (!normalized) continue;
      const limited = limits?.maxItemLength
        ? normalized.slice(0, limits.maxItemLength)
        : normalized;
      const dedupeKey = limited.toLowerCase();
      if (seen.has(dedupeKey)) continue;
      seen.add(dedupeKey);
      merged.push(limited);
      if (limits?.maxItems && merged.length >= limits.maxItems) {
        return merged;
      }
    }
  }
  return merged;
}

export function buildFieldMap(imported, current) {
  const prov = current?.field_provenance || {};
  return IMPORT_FIELD_DEFS
    .map(([section, label, uiKey, dataSection, field, provKey]) => {
      const importedRaw = imported?.[dataSection]?.[field];
      const currentRaw = current?.[dataSection]?.[field];
      const importedDisplay = displayVal(importedRaw);
      const currentDisplay = displayVal(currentRaw);
      const hasConflict =
        currentDisplay !== "\u2014" &&
        importedDisplay !== "\u2014" &&
        currentDisplay !== importedDisplay;
      const canMerge =
        hasConflict &&
        MERGEABLE_IMPORT_KEYS.has(uiKey) &&
        hasArrayValues(importedRaw) &&
        hasArrayValues(currentRaw);

      return {
        section,
        label,
        key: uiKey,
        importedDisplay,
        currentDisplay,
        currentProv: prov[provKey] || null,
        hasConflict,
        canMerge,
        mergedDisplay: canMerge ? displayVal(mergeDistinctValues(currentRaw, importedRaw, uiKey)) : null,
      };
    });
}

export function buildPartialProfile(imported, current, selectedKeys, mergeModes = new Map()) {
  const result = {};
  for (const [, , uiKey, dataSection, field] of IMPORT_FIELD_DEFS) {
    if (!selectedKeys.has(uiKey)) continue;
    const src = imported[dataSection];
    if (!src) continue;
    let value = src[field];
    if (mergeModes.get(uiKey) === "merge" && MERGEABLE_IMPORT_KEYS.has(uiKey)) {
      value = mergeDistinctValues(current?.[dataSection]?.[field], value, uiKey);
    } else if (Array.isArray(value)) {
      value = mergeDistinctValues([], value, uiKey);
    }
    if (value == null) continue;
    if (Array.isArray(value) && value.length === 0) continue;
    if (!result[dataSection]) result[dataSection] = {};
    result[dataSection][field] = Array.isArray(value) ? [...value] : value;
  }
  return result;
}
