import { api } from "../api.js";

let _profileInFlight = null;

export function getMyProfile() {
  return Promise.resolve(api.get("/api/v1/me"));
}

export function getMyProfileInFlight() {
  if (_profileInFlight) return _profileInFlight;
  _profileInFlight = getMyProfile()
    .finally(() => {
      _profileInFlight = null;
    });
  return _profileInFlight;
}

export function importMyCv(formData) {
  return api.post("/api/v1/me/cv-import", formData);
}

export function applyImportedProfile(source, partialProfile) {
  return Promise.resolve(api.post(`/api/v1/me/import-apply?source=${encodeURIComponent(source)}`, partialProfile));
}

export function updateMyProfile(payload) {
  return Promise.resolve(api.put("/api/v1/me", payload));
}

export function getMyConsents() {
  return api.get("/api/v1/me/consents");
}
