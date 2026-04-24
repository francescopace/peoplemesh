import { describe, it, expect } from "vitest";
import { renderRouteFallback } from "../../assets/js/utils/route-fallback.js";
import { getUserFacingErrorMessage } from "../../assets/js/utils/errors.js";
import {
  GEO_POSITIVE,
  isPositiveGeoReason,
  locationChipStyle,
  matchedTagStyle,
} from "../../assets/js/utils/match-visuals.js";

describe("route-fallback utility", () => {
  it("renders empty-state message and home link", () => {
    const container = document.createElement("div");
    container.innerHTML = "<p>stale</p>";

    renderRouteFallback(container, "Not found");

    const state = container.querySelector(".empty-state");
    expect(state).not.toBeNull();
    expect(state.textContent).toContain("Not found");
    expect(state.querySelector("a").getAttribute("href")).toBe("#/");
  });
});

describe("errors utility", () => {
  it("returns session-expired text on 401", () => {
    expect(getUserFacingErrorMessage({ status: 401 })).toBe(
      "Your session expired. Please sign in again.",
    );
  });

  it("returns fallback for 5xx and empty messages", () => {
    const fallback = "Try later";
    expect(getUserFacingErrorMessage({ status: 500 }, fallback)).toBe(fallback);
    expect(getUserFacingErrorMessage({ message: "   " }, fallback)).toBe(fallback);
    expect(getUserFacingErrorMessage({ message: "Request failed with status 400" }, fallback)).toBe(
      "Request failed with status 400",
    );
  });

  it("returns explicit user message for non-generic client errors", () => {
    expect(getUserFacingErrorMessage({ status: 400, message: "Email is required" })).toBe(
      "Email is required",
    );
  });
});

describe("match-visuals utility", () => {
  const colors = { bg: "#fff", color: "#111", border: "#333" };

  it("keeps positive geography reasons in shared allow-list", () => {
    expect(GEO_POSITIVE.has("same_country")).toBe(true);
    expect(isPositiveGeoReason("same_continent")).toBe(false);
    expect(isPositiveGeoReason("remote_friendly")).toBe(false);
    expect(isPositiveGeoReason("unsupported")).toBe(false);
  });

  it("returns positive and fallback chip style based on geography reason", () => {
    expect(locationChipStyle(colors, "same_country")).toContain("background:#fff");
    expect(locationChipStyle(colors, "different_region")).toContain("var(--color-gray-400)");
  });

  it("builds matched tag style from provided colors", () => {
    const style = matchedTagStyle(colors);
    expect(style).toContain("background:#fff");
    expect(style).toContain("border:1px solid #333");
  });
});
