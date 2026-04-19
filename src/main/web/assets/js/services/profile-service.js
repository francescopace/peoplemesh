import { api } from "../api.js";

export function getMyProfile() {
  return api.get("/api/v1/me");
}

export function importMyCv(formData) {
  return api.post("/api/v1/me/cv-import", formData);
}

export function applyImportedProfile(source, partialProfile) {
  return api.post(`/api/v1/me/import-apply?source=${encodeURIComponent(source)}`, partialProfile);
}

export function getMySkills() {
  return api.get("/api/v1/me/skills");
}

export function saveMySkills(payload) {
  return api.put("/api/v1/me/skills", payload);
}

export function updateMyProfile(payload) {
  return api.put("/api/v1/me", payload);
}

export function getMyConsents() {
  return api.get("/api/v1/me/consents");
}
