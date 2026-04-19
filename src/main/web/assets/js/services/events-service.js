import { api } from "../api.js";

export function listMyNotifications(limit = 50) {
  return api.get("/api/v1/me/notifications", { limit });
}

export function getMyActivity() {
  return api.get("/api/v1/me/activity");
}
