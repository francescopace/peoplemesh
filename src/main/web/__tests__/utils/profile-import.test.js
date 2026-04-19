import { describe, expect, it } from "vitest";
import { buildFieldMap, buildPartialProfile } from "../../assets/js/utils/profile-import.js";

describe("profile-import utils", () => {
  it("buildFieldMap marks mergeable array conflicts", () => {
    const imported = {
      professional: {
        skills_technical: ["Java", "Go"],
      },
    };
    const current = {
      professional: {
        skills_technical: ["JavaScript", "Java"],
      },
      field_provenance: {},
    };

    const field = buildFieldMap(imported, current, "github")
      .find((entry) => entry.key === "professional.skills_technical");

    expect(field).toBeTruthy();
    expect(field.hasConflict).toBe(true);
    expect(field.canMerge).toBe(true);
    expect(field.mergedDisplay).toContain("JavaScript");
    expect(field.mergedDisplay).toContain("Go");
  });

  it("buildPartialProfile only includes selected keys", () => {
    const imported = {
      identity: { birth_date: "1990-01-01", display_name: "Alice" },
      professional: { skills_technical: ["Java", "Go"] },
      geography: { country: "IT" },
    };
    const selected = new Set(["identity.birth_date", "professional.skills_technical"]);
    const result = buildPartialProfile(imported, {}, selected);

    expect(result.identity).toEqual({ birth_date: "1990-01-01" });
    expect(result.professional.skills_technical).toEqual(["Java", "Go"]);
    expect(result.geography).toBeUndefined();
  });

  it("buildPartialProfile merges selected array values when merge mode is set", () => {
    const imported = {
      professional: { skills_technical: ["Java", "Kubernetes"] },
    };
    const current = {
      professional: { skills_technical: ["java", "TypeScript"] },
    };
    const selected = new Set(["professional.skills_technical"]);
    const mergeModes = new Map([["professional.skills_technical", "merge"]]);
    const result = buildPartialProfile(imported, current, selected, mergeModes);

    expect(result.professional.skills_technical).toEqual(["java", "TypeScript", "Kubernetes"]);
  });

  it("buildPartialProfile merge clamps topics_frequent to max 50", () => {
    const currentTopics = Array.from({ length: 30 }, (_, i) => `current-${i}`);
    const importedTopics = Array.from({ length: 30 }, (_, i) => `imported-${i}`);
    const imported = {
      interests_professional: { topics_frequent: importedTopics },
    };
    const current = {
      interests_professional: { topics_frequent: currentTopics },
    };
    const selected = new Set(["interests.topics_frequent"]);
    const mergeModes = new Map([["interests.topics_frequent", "merge"]]);

    const result = buildPartialProfile(imported, current, selected, mergeModes);

    expect(result.interests_professional.topics_frequent).toHaveLength(50);
    expect(result.interests_professional.topics_frequent[0]).toBe("current-0");
    expect(result.interests_professional.topics_frequent[49]).toBe("imported-19");
  });
});
