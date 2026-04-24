import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("skills-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/skills-service.js");
  });

  it("lists global skills", async () => {
    apiMock.get.mockResolvedValue([]);
    await service.listSkills({ page: 0, size: 20 });
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/skills", { page: 0, size: 20 });
  });

  it("imports CSV and triggers cleanup", async () => {
    await service.importSkillsCsv(new ArrayBuffer(8));
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/skills/import", expect.any(ArrayBuffer));

    await service.cleanupUnusedSkills();
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/skills/cleanup-unused", {});
  });

  it("loads suggestions by aliases", async () => {
    await service.suggestSkills("jav", 8);
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/skills/suggest", { q: "jav", limit: 8 });
  });
});
