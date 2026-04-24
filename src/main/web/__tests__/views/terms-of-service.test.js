import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../assets/js/platform-info.js", () => ({
  getPlatformInfo: vi.fn(() => Promise.resolve({})),
}));

import { renderTermsOfService } from "../../assets/js/views/terms-of-service.js";
import { getPlatformInfo } from "../../assets/js/platform-info.js";

describe("renderTermsOfService()", () => {
  beforeEach(() => {
    getPlatformInfo.mockResolvedValue({});
  });

  it("renders into the container", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.innerHTML).not.toBe("");
  });

  it("contains the page title", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.querySelector("h1").textContent).toBe("Terms of Service");
  });

  it("contains key section headings", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    const headings = [...container.querySelectorAll("h2")].map((h) => h.textContent);
    expect(headings).toContain("1. Acceptance");
    expect(headings).toContain("4. Acceptable Use");
    expect(headings).toContain("7. Privacy");
    expect(headings).toContain("14. Governing Law");
  });

  it("contains the brand element", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("PeopleMesh");
  });

  it("contains footer", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("contains privacy policy and privacy dashboard links", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.querySelector('a[href="#/privacy_policy"]')).not.toBeNull();
    expect(container.querySelector('a[href="#/privacy"]')).not.toBeNull();
  });

  it("contains contact section", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("contact the Operator");
  });

  it("contains back to home link", async () => {
    const container = document.createElement("div");
    await renderTermsOfService(container);

    const back = container.querySelector('a[href="#/"]');
    expect(back).not.toBeNull();
  });

  it("shows operator name when configured", async () => {
    getPlatformInfo.mockResolvedValue({ organizationName: "Acme Corp" });
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("Acme Corp");
  });

  it("shows contact email when configured", async () => {
    getPlatformInfo.mockResolvedValue({ contactEmail: "legal@acme.com" });
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("legal@acme.com");
  });

  it("shows governing law when configured", async () => {
    getPlatformInfo.mockResolvedValue({ governingLaw: "Italian law, courts of Milan" });
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("Italian law, courts of Milan");
  });

  it("falls back to generic governing law when not configured", async () => {
    getPlatformInfo.mockResolvedValue({});
    const container = document.createElement("div");
    await renderTermsOfService(container);

    expect(container.textContent).toContain("laws of the jurisdiction where the Operator is established");
  });
});
