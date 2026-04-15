import { describe, it, expect } from "vitest";
import { deriveInitialsFromName, deriveInitials } from "../../assets/js/utils/initials.js";

describe("deriveInitialsFromName", () => {
  it("returns two-letter initials from first and last name", () => {
    expect(deriveInitialsFromName("Alice Smith")).toBe("AS");
  });

  it("uses first two chars for single-word name", () => {
    expect(deriveInitialsFromName("Alice")).toBe("AL");
  });

  it("handles three-part names (first + last initial)", () => {
    expect(deriveInitialsFromName("Alice Marie Smith")).toBe("AS");
  });

  it("returns empty string for null/undefined/blank", () => {
    expect(deriveInitialsFromName(null)).toBe("");
    expect(deriveInitialsFromName(undefined)).toBe("");
    expect(deriveInitialsFromName("")).toBe("");
    expect(deriveInitialsFromName("   ")).toBe("");
  });

  it("uppercases the result", () => {
    expect(deriveInitialsFromName("bob jones")).toBe("BJ");
  });

  it("trims leading/trailing whitespace", () => {
    expect(deriveInitialsFromName("  Jane Doe  ")).toBe("JD");
  });

  it("handles extra internal spaces", () => {
    expect(deriveInitialsFromName("Jane   Doe")).toBe("JD");
  });
});

describe("deriveInitials", () => {
  it("uses display_name when available", () => {
    expect(deriveInitials({ display_name: "Alice Smith" })).toBe("AS");
  });

  it("uses displayName (camelCase) as fallback", () => {
    expect(deriveInitials({ displayName: "Bob Jones" })).toBe("BJ");
  });

  it("uses name as fallback", () => {
    expect(deriveInitials({ name: "Charlie" })).toBe("CH");
  });

  it("falls back to provider initials when name is empty", () => {
    expect(deriveInitials({ provider: "github" })).toBe("GI");
  });

  it("returns 'U' when nothing is available", () => {
    expect(deriveInitials({})).toBe("U");
    expect(deriveInitials(null)).toBe("U");
    expect(deriveInitials(undefined)).toBe("U");
  });

  it("prefers display_name over provider", () => {
    expect(deriveInitials({ display_name: "Pat Ex", provider: "google" })).toBe("PE");
  });
});
