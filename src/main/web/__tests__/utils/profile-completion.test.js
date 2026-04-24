import { describe, expect, it } from "vitest";
import {
  computeProfileScore,
  listProfileCompletionHints,
} from "../../assets/js/utils/profile-completion.js";

describe("profile-completion utils", () => {
  it("computeProfileScore returns full score for complete profile", () => {
    const score = computeProfileScore({
      identity: { display_name: "Alice" },
      professional: { roles: ["Engineer"], skills_technical: ["Java"], industries: ["Software"] },
      personal: { hobbies: ["climbing"] },
      interests_professional: { learning_areas: ["ai"] },
      geography: { country: "IT" },
      consent: { explicit: true },
    });
    expect(score).toBe(90);
  });

  it("listProfileCompletionHints returns missing sections", () => {
    const hints = listProfileCompletionHints({
      professional: { roles: [], skills_technical: [] },
      personal: { hobbies: [], sports: [] },
      geography: {},
    });
    expect(hints).toEqual(["roles", "skills", "interests", "location"]);
  });
});
