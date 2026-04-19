import { api } from "../api.js";

export function getJobMatches(jobId) {
  return api.get(`/api/v1/matches/${jobId}`, { type: "PEOPLE" });
}
