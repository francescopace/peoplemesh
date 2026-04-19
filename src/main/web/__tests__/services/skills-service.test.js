import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("skills-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/skills-service.js");
  });

  it("lists catalogs", async () => {
    apiMock.get.mockResolvedValue([]);
    await service.listSkillCatalogs();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/skills");
  });

  it("creates and updates a catalog", async () => {
    await service.createSkillCatalog({ name: "Core" });
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/skills", { name: "Core" });

    await service.updateSkillCatalog("cat-1", { name: "Core v2" });
    expect(apiMock.put).toHaveBeenCalledWith("/api/v1/skills/cat-1", { name: "Core v2" });
  });

  it("imports csv buffer and loads definitions", async () => {
    const buf = new ArrayBuffer(8);
    await service.importSkillCatalogCsv("cat-1", buf);
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/skills/cat-1/import", buf);

    await service.listSkillDefinitions("cat-1", { page: 1, size: 50, category: "backend" });
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/skills/cat-1/definitions", {
      page: 1,
      size: 50,
      category: "backend",
    });
  });
});
