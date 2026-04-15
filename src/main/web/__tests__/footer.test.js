import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderFooter } from "../assets/js/footer.js";

describe("renderFooter()", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-04-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns an HTML string with footer tag", () => {
    const html = renderFooter();
    expect(html).toContain("<footer");
    expect(html).toContain("</footer>");
  });

  it("includes the current year", () => {
    const html = renderFooter();
    expect(html).toContain("2026");
  });

  it("includes PeopleMesh brand name", () => {
    const html = renderFooter();
    expect(html).toContain("PeopleMesh");
  });

  it("includes privacy policy link", () => {
    const html = renderFooter();
    expect(html).toContain('href="#/privacy_policy"');
    expect(html).toContain("Privacy Policy");
  });

  it("includes terms of service link", () => {
    const html = renderFooter();
    expect(html).toContain('href="#/terms_of_service"');
    expect(html).toContain("Terms of Service");
  });

  it("applies default class", () => {
    const html = renderFooter();
    expect(html).toContain("landing-footer");
  });

  it("adds extra class when provided", () => {
    const html = renderFooter({ extraClass: "app-footer" });
    expect(html).toContain("landing-footer app-footer");
  });

  it("does not add extra class when empty", () => {
    const html = renderFooter({ extraClass: "" });
    expect(html).toContain('class="landing-footer"');
  });
});
