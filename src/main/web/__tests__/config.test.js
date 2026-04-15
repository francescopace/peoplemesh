import { describe, it, expect } from "vitest";
import { Config } from "../config.js";

describe("Config", () => {
  it("has apiBase property", () => {
    expect(Config).toHaveProperty("apiBase");
    expect(typeof Config.apiBase).toBe("string");
  });

  it("has devMode boolean", () => {
    expect(typeof Config.devMode).toBe("boolean");
  });

  it("has providers array", () => {
    expect(Array.isArray(Config.providers)).toBe(true);
    expect(Config.providers.length).toBeGreaterThan(0);
  });

  it("includes expected OAuth providers", () => {
    expect(Config.providers).toContain("google");
    expect(Config.providers).toContain("github");
  });
});
