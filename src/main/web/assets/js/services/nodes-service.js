import { api } from "../api.js";

export function getNode(nodeId) {
  return api.get(`/api/v1/nodes/${nodeId}`);
}

export function getNodeProfile(nodeId) {
  return api.get(`/api/v1/nodes/${nodeId}/profile`);
}

export function getNodeSkills(nodeId) {
  return api.get(`/api/v1/nodes/${nodeId}/skills`);
}
