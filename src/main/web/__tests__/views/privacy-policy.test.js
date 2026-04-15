import { describe, it, expect } from "vitest";
import { renderPrivacyPolicy } from "../../assets/js/views/privacy-policy.js";

describe("renderPrivacyPolicy()", () => {
  it("renders into the container", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    expect(container.innerHTML).not.toBe("");
  });

  it("contains the page title", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    expect(container.querySelector("h1").textContent).toBe("Privacy Policy");
  });

  it("contains all section headings", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    const headings = [...container.querySelectorAll("h2")].map((h) => h.textContent);
    expect(headings).toContain("1. Data Controller");
    expect(headings).toContain("2. Data We Collect");
    expect(headings).toContain("10. Cookies");
    expect(headings).toContain("12. Contact & Complaints");
  });

  it("contains privacy dashboard links", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    const links = [...container.querySelectorAll('a[href="#/privacy"]')];
    expect(links.length).toBeGreaterThan(0);
  });

  it("contains the brand element", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    expect(container.textContent).toContain("PeopleMesh");
  });

  it("contains footer", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("contains back to home link", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    const back = container.querySelector(".legal-back");
    expect(back).not.toBeNull();
    expect(back.textContent).toContain("Back to Home");
  });

  it("includes consent scope table", () => {
    const container = document.createElement("div");
    renderPrivacyPolicy(container);

    const tables = container.querySelectorAll("table");
    expect(tables.length).toBeGreaterThan(0);
    expect(container.textContent).toContain("professional_matching");
  });
});
