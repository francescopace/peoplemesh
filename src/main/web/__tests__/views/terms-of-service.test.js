import { describe, it, expect } from "vitest";
import { renderTermsOfService } from "../../assets/js/views/terms-of-service.js";

describe("renderTermsOfService()", () => {
  it("renders into the container", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.innerHTML).not.toBe("");
  });

  it("contains the page title", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.querySelector("h1").textContent).toBe("Terms of Service");
  });

  it("contains key section headings", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    const headings = [...container.querySelectorAll("h2")].map((h) => h.textContent);
    expect(headings).toContain("1. Acceptance");
    expect(headings).toContain("4. Acceptable Use");
    expect(headings).toContain("7. Privacy");
    expect(headings).toContain("14. Governing Law");
  });

  it("contains the brand element", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.textContent).toContain("PeopleMesh");
  });

  it("contains footer", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("contains privacy policy and privacy dashboard links", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.querySelector('a[href="#/privacy_policy"]')).not.toBeNull();
    expect(container.querySelector('a[href="#/privacy"]')).not.toBeNull();
  });

  it("contains contact email", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    expect(container.textContent).toContain("legal@peoplemesh.org");
  });

  it("contains back to home link", () => {
    const container = document.createElement("div");
    renderTermsOfService(container);

    const back = container.querySelector('a[href="#/"]');
    expect(back).not.toBeNull();
  });
});
