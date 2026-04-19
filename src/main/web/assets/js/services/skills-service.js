import { api } from "../api.js";

export function listSkillCatalogs() {
  return api.get("/api/v1/skills");
}

export function createSkillCatalog(payload) {
  return api.post("/api/v1/skills", payload);
}

export function updateSkillCatalog(catalogId, payload) {
  return api.put(`/api/v1/skills/${catalogId}`, payload);
}

export function deleteSkillCatalog(catalogId) {
  return api.delete(`/api/v1/skills/${catalogId}`);
}

export function getSkillCatalog(catalogId) {
  return api.get(`/api/v1/skills/${catalogId}`);
}

export function importSkillCatalogCsv(catalogId, fileBuffer) {
  return api.post(`/api/v1/skills/${catalogId}/import`, fileBuffer);
}

export function listSkillCategories(catalogId) {
  return api.get(`/api/v1/skills/${catalogId}/categories`);
}

export function listSkillDefinitions(catalogId, query) {
  return api.get(`/api/v1/skills/${catalogId}/definitions`, query);
}
