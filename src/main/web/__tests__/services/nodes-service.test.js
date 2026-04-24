import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("nodes-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = { get: vi.fn() };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/nodes-service.js");
  });

  it("loads node, profile and skills by id", async () => {
    await service.getNode("n1");
    await service.getNodeProfile("n1");
    await service.getNodeSkills("n1");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/nodes/n1");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/nodes/n1/profile");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/nodes/n1/skills");
  });
});
