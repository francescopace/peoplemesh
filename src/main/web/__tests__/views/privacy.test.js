import { beforeEach, describe, expect, it, vi } from "vitest";

let renderPrivacy;
let privacyServiceMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("privacy view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/privacy";

    privacyServiceMock = {
      exportMyData: vi.fn(),
      deleteMyAccount: vi.fn(),
      listMyConsents: vi.fn(),
      grantMyConsent: vi.fn(),
      revokeMyConsent: vi.fn(),
    };

    vi.doMock("../../assets/js/services/privacy-service.js", () => privacyServiceMock);
    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: { logout: vi.fn() },
    }));

    const mod = await import("../../assets/js/views/privacy.js");
    renderPrivacy = mod.renderPrivacy;
  });

  it("renders consent cards from backend scopes", async () => {
    privacyServiceMock.listMyConsents.mockResolvedValue({
      scopes: ["professional_matching", "embedding_processing"],
      active: ["professional_matching"],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPrivacy(container);
    await flushPromises();

    expect(container.textContent).toContain("Consent Management");
    expect(container.textContent).toContain("Professional Matching");
    expect(container.textContent).toContain("Embedding Processing");
  });

  it("triggers grant endpoint when enabling inactive scope", async () => {
    privacyServiceMock.listMyConsents.mockResolvedValue({
      scopes: ["embedding_processing"],
      active: [],
    });
    privacyServiceMock.grantMyConsent.mockResolvedValue({});

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPrivacy(container);
    await flushPromises();

    const toggle = container.querySelector(".priv-consent-toggle");
    expect(toggle).toBeTruthy();
    toggle.click();
    await flushPromises();

    expect(privacyServiceMock.grantMyConsent).toHaveBeenCalledWith("embedding_processing");
  });
});
