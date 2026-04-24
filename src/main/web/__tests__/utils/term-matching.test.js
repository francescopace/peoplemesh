import { describe, it, expect } from "vitest";
import { termsMatch, normalizeTerm } from "../../assets/js/utils/term-matching.js";

describe("term-matching utils", () => {
  it("normalizes punctuation and casing", () => {
    expect(normalizeTerm(" C++ ")).toBe("c");
    expect(normalizeTerm("Node.js")).toBe("node js");
  });

  it("prevents Java matching JavaScript", () => {
    expect(termsMatch("Java", "JavaScript")).toBe(false);
  });

  it("keeps token subset matching for multi-token skills", () => {
    expect(termsMatch("React", "React Native")).toBe(true);
  });
});
