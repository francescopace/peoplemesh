import { api } from "../api.js";

export function listSkills(query) {
  return api.get("/api/v1/skills", query);
}

export function suggestSkills(q, limit = 20) {
  return api.get("/api/v1/skills/suggest", { q, limit });
}

export function importSkillsCsv(fileBuffer) {
  return api.post("/api/v1/skills/import", fileBuffer);
}

export function cleanupUnusedSkills() {
  return api.post("/api/v1/skills/cleanup-unused", {});
}
