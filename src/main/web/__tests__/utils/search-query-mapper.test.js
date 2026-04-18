import { describe, it, expect } from "vitest";
import {
  adaptMatchesToSearchResponse,
  buildProfileSchemaFromParsedQuery,
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

  it("infers community tab from parsed keywords", () => {
    const parsed = {
      must_have: { roles: [], skills: [] },
      keywords: ["community", "devrel"],
      embedding_text: "community in italy",
    };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("COMMUNITY");
  });

  it("falls back to legacy heuristics when result_scope is unknown", () => {
    const parsed = {
      result_scope: "unknown",
      must_have: { roles: [], skills: [] },
      keywords: ["community"],
      embedding_text: "community data engineering in europe",
    };
    expect(inferAutoTypeFromParsedQuery(parsed)).toBe("COMMUNITY");
  });

  it("maps parsed query to minimal profile schema", () => {
    const parsed = {
      must_have: {
        roles: ["Developer"],
        skills: ["Java"],
        languages: ["English"],
        location: ["Italy"],
      },
      nice_to_have: { skills: ["Kubernetes"] },
      seniority: "senior",
      keywords: ["backend"],
    };
    const schema = buildProfileSchemaFromParsedQuery(parsed);
    expect(schema.professional.roles).toEqual(["Developer"]);
    expect(schema.professional.skills_technical).toEqual(["Java", "Kubernetes"]);
    expect(schema.professional.seniority).toBe("SENIOR");
    expect(schema.geography.country).toBe("IT");
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
        breakdown: { commonItems: ["Java"] },
      },
    ]);
    expect(adapted.results).toHaveLength(1);
    expect(adapted.results[0].resultType).toBe("profile");
    expect(adapted.results[0].breakdown.matchedMustHaveSkills).toEqual(["Java"]);
  });

  it("maps profile tab to PEOPLE type filter", () => {
    expect(toMatchesTypeFilter("profile")).toBe("PEOPLE");
    expect(toMatchesTypeFilter("COMMUNITY")).toBe("COMMUNITY");
    expect(toMatchesTypeFilter("")).toBe("");
  });
});
