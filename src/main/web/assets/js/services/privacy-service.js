import { api } from "../api.js";

export function exportMyData() {
  return api.get("/api/v1/me/export");
}

export function deleteMyAccount() {
  return api.delete("/api/v1/me");
}

export function listMyConsents() {
  return api.get("/api/v1/me/consents");
}

export function grantMyConsent(scope) {
  return api.post(`/api/v1/me/consents/${scope}`);
}

export function revokeMyConsent(scope) {
  return api.delete(`/api/v1/me/consents/${scope}`);
}
