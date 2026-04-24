import { describe, it, expect } from "vitest";
import { COUNTRIES } from "../../assets/js/utils/countries.js";

describe("COUNTRIES", () => {
  it("is a non-empty array", () => {
    expect(Array.isArray(COUNTRIES)).toBe(true);
    expect(COUNTRIES.length).toBeGreaterThan(0);
  });

  it("each entry is a [code, name] tuple", () => {
    for (const entry of COUNTRIES) {
      expect(entry).toHaveLength(2);
      expect(typeof entry[0]).toBe("string");
      expect(typeof entry[1]).toBe("string");
      expect(entry[0]).toHaveLength(2);
    }
  });

  it("contains expected countries", () => {
    const codes = COUNTRIES.map(([c]) => c);
    expect(codes).toContain("US");
    expect(codes).toContain("IT");
    expect(codes).toContain("DE");
    expect(codes).toContain("GB");
    expect(codes).toContain("JP");
  });

  it("codes are uppercase", () => {
    for (const [code] of COUNTRIES) {
      expect(code).toBe(code.toUpperCase());
    }
  });

  it("has no duplicate codes", () => {
    const codes = COUNTRIES.map(([c]) => c);
    expect(new Set(codes).size).toBe(codes.length);
  });
});
