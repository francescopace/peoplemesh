import { describe, it, expect } from "vitest";
import {
  adaptMatchesToSearchResponse,
  inferAutoTypeFromParsedQuery,
  toMatchesTypeFilter,
} from "../../assets/js/utils/search-query-mapper.js";

describe("search-query-mapper", () => {
  it('maps result_scope "all" to All tab', () => {
    const parsed = { result_scope: "all" };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("");
  });

  it('maps result_scope "communities" to community tab', () => {
    const parsed = { result_scope: "communities" };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("COMMUNITY");
  });

  it('maps result_scope "jobs" to jobs tab', () => {
    const parsed = { result_scope: "jobs" };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("JOB");
  });

  it('maps result_scope "people" to people tab', () => {
    const parsed = { result_scope: "people" };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("PEOPLE");
  });

  it("keeps All tab when result_scope is missing", () => {
    const parsed = {
      must_have: { roles: [], skills: [] },
      keywords: ["community", "devrel"],
      embedding_text: "community in italy",
    };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("");
  });

  it("keeps All tab when result_scope is unknown", () => {
    const parsed = {
      result_scope: "unknown",
      must_have: { roles: [], skills: [] },
      keywords: ["community"],
      embedding_text: "community data engineering in europe",
    };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("");
  });

  it("adapts mesh matches response to search response shape", () => {
    const adapted = adaptMatchesToSearchResponse([
      {
        id: "p-1",
        nodeType: "PEOPLE",
        title: "Alice",
        score: 0.8,
        country: "IT",
        person: {
          roles: ["Developer"],
          skillsTechnical: ["Java"],
          toolsAndTech: ["Docker"],
        },
        breakdown: { commonItems: ["Java"], geographyReason: "same_country", geographyScore: 1 },
      },
    ]);
    expect(adapted.results).toHaveLength(1);
    expect(adapted.results[0].resultType).toBe("profile");
    expect(adapted.results[0].breakdown.matchedMustHaveSkills).toEqual(["Java"]);
    expect(adapted.results[0].breakdown.geographyReason).toBe("same_country");
  });

  it("keeps node type filter values unchanged", () => {
    expect(toMatchesTypeFilter("PEOPLE")).toBe("PEOPLE");
    expect(toMatchesTypeFilter("COMMUNITY")).toBe("COMMUNITY");
    expect(toMatchesTypeFilter("")).toBe("");
  });
});
