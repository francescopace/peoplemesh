import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("matches-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/matches-service.js");
  });

  it("builds prompt endpoint with paging params", async () => {
    apiMock.post.mockResolvedValue({ results: [] });
    await service.fetchPromptMatches({ queryText: "java", limit: 10, offset: 20 });
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/matches/prompt?limit=10&offset=20", { query: "java" });
  });

  it("builds structured endpoint while filtering empty params", async () => {
    apiMock.post.mockResolvedValue({ results: [] });
    await service.fetchStructuredMatches({
      schema: { must_have: {} },
      type: "",
      country: "IT",
      limit: 10,
      offset: 0,
    });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/v1/matches?country=IT&limit=10&offset=0",
      { must_have: {} }
    );
  });

  it("requests my mesh via GET query object", async () => {
    apiMock.get.mockResolvedValue([]);
    await service.fetchMyMeshMatches({ type: "PEOPLE", country: "IT", limit: 9, offset: 0 });
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/matches/me", {
      type: "PEOPLE",
      country: "IT",
      limit: 9,
      offset: 0,
    });
  });
});
