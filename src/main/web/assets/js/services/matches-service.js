import { api } from "../api.js";
import { adaptMatchesToSearchResponse } from "../utils/search-query-mapper.js";

function toQueryString(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value != null && value !== "") {
      query.set(key, String(value));
    }
  });
  return query.toString();
}

export async function fetchPromptMatches({ queryText, limit, offset }) {
  const qs = toQueryString({ limit, offset });
  return api.post(`/api/v1/matches/prompt?${qs}`, { query: queryText });
}

export async function fetchStructuredMatches({ schema, type, country, limit, offset }) {
  const qs = toQueryString({ type, country, limit, offset });
  return api.post(`/api/v1/matches?${qs}`, schema || {});
}

export async function fetchMyMeshMatches({ type, country, limit, offset }) {
  return api.get("/api/v1/matches/me", { type, country, limit, offset });
}

export async function fetchSearchResultsPage({
  mode,
  queryText,
  parsedQuery,
  type,
  country,
  limit,
  offset,
}) {
  if (mode === "matches") {
    const matches = await fetchStructuredMatches({
      schema: parsedQuery || {},
      type,
      country,
      limit,
      offset,
    });
    const adapted = adaptMatchesToSearchResponse(matches, parsedQuery);
    return {
      results: adapted.results || [],
      parsedQuery: null,
    };
  }

  const promptPayload = await fetchPromptMatches({
    queryText,
    limit,
    offset,
  });
  return {
    results: promptPayload?.results || [],
    parsedQuery: promptPayload?.parsedQuery || null,
  };
}
