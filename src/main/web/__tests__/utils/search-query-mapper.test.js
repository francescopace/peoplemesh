import { describe, it, expect } from "vitest";
import {
  adaptMatchesToSearchResponse,
  buildProfileSchemaFromParsedQuery,
  inferAutoTypeFromParsedQuery,
  toMatchesTypeFilter,
} from "../../assets/js/utils/search-query-mapper.js";

describe("search-query-mapper", () => {
  it("infers community tab from parsed keywords", () => {
    const parsed = {
      must_have: { roles: [], skills: [] },
      keywords: ["community", "devrel"],
      embedding_text: "community in italy",
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
