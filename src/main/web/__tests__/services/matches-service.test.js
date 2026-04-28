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
    await service.fetchPromptMatches({ queryText: "java", limit: 10 });
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/matches/prompt?limit=10", { query: "java" });
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

  it("fetches prompt search page and returns parsedQuery", async () => {
    apiMock.post.mockResolvedValue({
      parsedQuery: { keywords: ["java"] },
      results: [{ id: "p-1", resultType: "profile" }],
    });

    const page = await service.fetchSearchResultsPage({
      mode: "prompt",
      queryText: "java",
      parsedQuery: null,
      type: "",
      country: "",
      limit: 10,
      offset: 0,
    });

    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/matches/prompt?limit=10", { query: "java" });
    expect(page).toEqual({
      parsedQuery: { keywords: ["java"] },
      results: [{ id: "p-1", resultType: "profile" }],
    });
  });

  it("fetches structured search page and adapts matches response", async () => {
    apiMock.post.mockResolvedValue([
      {
        id: "n-1",
        nodeType: "PEOPLE",
        title: "Alice",
        score: 0.87,
        person: {
          roles: ["Engineer"],
          skillsTechnical: ["Java"],
          toolsAndTech: ["Quarkus"],
        },
        breakdown: { commonItems: ["Java"] },
      },
    ]);

    const page = await service.fetchSearchResultsPage({
      mode: "matches",
      queryText: "unused",
      parsedQuery: {
        must_have: { skills: ["Java"] },
        nice_to_have: { skills: [] },
      },
      type: "PEOPLE",
      country: "IT",
      limit: 10,
      offset: 20,
    });

    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/v1/matches?type=PEOPLE&country=IT&limit=10&offset=20",
      {
        must_have: { skills: ["Java"] },
        nice_to_have: { skills: [] },
      }
    );
    expect(page.parsedQuery).toBeNull();
    expect(page.results).toHaveLength(1);
    expect(page.results[0]).toMatchObject({
      id: "n-1",
      resultType: "profile",
      displayName: "Alice",
      skillsTechnical: ["Java"],
    });
  });
});
