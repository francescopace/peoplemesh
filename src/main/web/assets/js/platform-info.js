import { api } from "./api.js";

let cached = null;
let inFlight = null;

export async function getPlatformInfo() {
  if (cached) return cached;
  if (inFlight) return inFlight;

  inFlight = (async () => {
    try {
      const response = await api.get("/api/v1/info");
      cached = response ?? {};
    } catch {
      cached = {};
    } finally {
      inFlight = null;
    }
    return cached;
  })();

  return inFlight;
}

export async function getOrganizationName() {
  const info = await getPlatformInfo();
  const organizationName = info?.organizationName;
  if (typeof organizationName !== "string") return null;
  const normalizedOrganizationName = organizationName.trim();
  return normalizedOrganizationName || null;
}
