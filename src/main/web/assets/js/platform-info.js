import { api } from "./api.js";

let cached = null;

export async function getPlatformInfo() {
  if (cached) return cached;
  try {
    cached = await api.get("/api/v1/info");
  } catch {
    cached = {};
  }
  return cached;
}
