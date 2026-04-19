import { api } from "../api.js";

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
