import { api } from "../api.js";

export function getSystemStatistics() {
  return api.get("/api/v1/system/statistics");
}
