import { Config } from "../../../config.js";
import { api } from "../api.js";

export function getCurrentAuthIdentity() {
  return api.get("/api/v1/auth/identity");
}

export function logoutSession() {
  return api.post("/api/v1/auth/logout");
}

export function finalizeImportFromOAuthCallback(provider, { code, state }) {
  const safeProvider = encodeURIComponent(String(provider || "").trim());
  return api.get(`/api/v1/auth/callback/${safeProvider}/import-finalize`, {
    code,
    state,
  });
}

export function buildAuthLoginUrl(provider, query = {}) {
  const safeProvider = encodeURIComponent(String(provider || "").trim());
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value != null && value !== "") {
      params.set(key, String(value));
    }
  });
  const queryString = params.toString();
  const suffix = queryString ? `?${queryString}` : "";
  return `${Config.apiBase}/api/v1/auth/login/${safeProvider}${suffix}`;
}
