import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../assets/js/platform-info.js", () => ({
  getPlatformInfo: vi.fn(() => Promise.resolve({})),
}));

import { renderPrivacyPolicy } from "../../assets/js/views/privacy-policy.js";
import { getPlatformInfo } from "../../assets/js/platform-info.js";

describe("renderPrivacyPolicy()", () => {
  beforeEach(() => {
    getPlatformInfo.mockResolvedValue({});
  });

  it("renders into the container", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.innerHTML).not.toBe("");
  });

  it("contains the page title", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.querySelector("h1").textContent).toBe("Privacy Policy");
  });

  it("contains all section headings", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    const headings = [...container.querySelectorAll("h2")].map((h) => h.textContent);
    expect(headings).toContain("1. Data Controller");
    expect(headings).toContain("2. Data We Collect");
    expect(headings).toContain("10. Cookies");
    expect(headings).toContain("12. Contact & Complaints");
  });

  it("contains privacy dashboard links", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    const links = [...container.querySelectorAll('a[href="#/privacy"]')];
    expect(links.length).toBeGreaterThan(0);
  });

  it("contains the brand element", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("PeopleMesh");
  });

  it("contains footer", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("contains back to home link", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    const back = container.querySelector(".legal-back");
    expect(back).not.toBeNull();
    expect(back.textContent).toContain("Back to Home");
  });

  it("includes consent scope table", async () => {
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    const tables = container.querySelectorAll("table");
    expect(tables.length).toBeGreaterThan(0);
    expect(container.textContent).toContain("professional_matching");
  });

  it("shows operator name when configured", async () => {
    getPlatformInfo.mockResolvedValue({ organizationName: "Acme Corp" });
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("Acme Corp");
  });

  it("shows DPO details when configured", async () => {
    getPlatformInfo.mockResolvedValue({ dpoName: "Jane Doe", dpoEmail: "dpo@acme.com" });
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("Jane Doe");
    expect(container.textContent).toContain("dpo@acme.com");
  });

  it("shows contact email when configured", async () => {
    getPlatformInfo.mockResolvedValue({ contactEmail: "privacy@acme.com" });
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("privacy@acme.com");
  });

  it("shows data location when configured", async () => {
    getPlatformInfo.mockResolvedValue({ dataLocation: "EU, Frankfurt" });
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("EU, Frankfurt");
  });

  it("falls back to generic text when not configured", async () => {
    getPlatformInfo.mockResolvedValue({});
    const container = document.createElement("div");
    await renderPrivacyPolicy(container);

    expect(container.textContent).toContain("organisation or individual operating this instance");
  });
});
