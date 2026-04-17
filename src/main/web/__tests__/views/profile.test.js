import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let renderProfile;
let buildPartialProfile;
let apiMock;
let toastMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function sampleProfile(overrides = {}) {
  return {
    professional: { roles: ["Engineer"], skills_technical: ["Java"] },
    contacts: {},
    interests_professional: {},
    personal: {},
    geography: { country: "IT", city: "Rome", timezone: "Europe/Rome" },
    identity: {
      display_name: "OAuth User",
      first_name: "OAuth",
      last_name: "User",
      email: "oauth@example.com",
      photo_url: "https://example.com/avatar.jpg",
      company: "PeopleMesh",
      birth_date: "1990-01-01",
    },
    field_provenance: {
      "identity.display_name": "google",
      "identity.email": "google",
      "identity.birth_date": "manual",
    },
    ...overrides,
  };
}

describe("profile view identity/import constraints", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/profile";

    const profile = sampleProfile();
    apiMock = {
      get: vi.fn().mockImplementation((path) => {
        if (path === "/api/v1/me") return Promise.resolve(profile);
        if (path === "/api/v1/me/skills") return Promise.resolve([]);
        return Promise.resolve(null);
      }),
      post: vi.fn().mockResolvedValue({}),
      put: vi.fn().mockResolvedValue({}),
    };
    toastMock = vi.fn();

    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: {
        isProviderConfigured: vi.fn().mockReturnValue(false),
      },
    }));
    vi.doMock("../../assets/js/ui.js", async () => {
      const actual = await vi.importActual("../../assets/js/ui.js");
      return {
        ...actual,
        toast: toastMock,
        toastForPromise: async (run) => run(),
      };
    });

    const mod = await import("../../assets/js/views/profile.js");
    renderProfile = mod.renderProfile;
    buildPartialProfile = mod.buildPartialProfile;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("allows editing only identity birth date", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const identityCard = [...container.querySelectorAll(".profile-card")].find((card) =>
      card.querySelector(".profile-card-title")?.textContent.includes("Identity")
    );
    expect(identityCard).toBeTruthy();

    identityCard.querySelector(".profile-edit-link").click();

    expect(identityCard.querySelector('[data-field="birthDate"]')).not.toBeNull();
    expect(identityCard.querySelector('[data-field="displayName"]')).toBeNull();
    expect(identityCard.querySelector('[data-field="firstName"]')).toBeNull();
    expect(identityCard.querySelector('[data-field="lastName"]')).toBeNull();
    expect(identityCard.querySelector('[data-field="email"]')).toBeNull();
    expect(identityCard.querySelector('[data-field="photoUrl"]')).toBeNull();
    expect(identityCard.querySelector('[data-field="company"]')).toBeNull();
  });

  it("sends only identity.birth_date in /api/v1/me update payload", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const identityCard = [...container.querySelectorAll(".profile-card")].find((card) =>
      card.querySelector(".profile-card-title")?.textContent.includes("Identity")
    );
    identityCard.querySelector(".profile-edit-link").click();

    const birthInput = identityCard.querySelector('[data-field="birthDate"]');
    birthInput.value = "1992-04-12";

    identityCard.querySelector(".profile-inline-actions .btn-primary").click();
    await flushPromises();

    expect(apiMock.put).toHaveBeenCalled();
    const [url, payload] = apiMock.put.mock.calls[0];
    expect(url).toBe("/api/v1/me");
    expect(payload.identity).toEqual({ birth_date: "1992-04-12" });
    expect(payload.identity.display_name).toBeUndefined();
    expect(payload.identity.email).toBeUndefined();
  });

  it("import modal exposes only Identity Birth Date and payload builder keeps only that", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      data: {
        type: "import-result",
        source: "github",
        imported: {
          identity: {
            display_name: "Imported Name",
            email: "imported@example.com",
            birth_date: "1988-05-17",
          },
          professional: {
            roles: ["Architect"],
          },
        },
      },
    }));

    const dialog = document.querySelector(".import-preview-dialog");
    expect(dialog).not.toBeNull();

    const labels = [...dialog.querySelectorAll(".import-preview-field-label")].map((el) => el.textContent.trim());
    expect(labels).toContain("Birth Date");
    expect(labels).not.toContain("Display Name");
    expect(labels).not.toContain("First Name");
    expect(labels).not.toContain("Last Name");
    expect(labels).not.toContain("Email");
    expect(labels).not.toContain("Photo");
    expect(labels).not.toContain("Company");

    const payload = buildPartialProfile(
      {
        identity: {
          display_name: "Imported Name",
          email: "imported@example.com",
          birth_date: "1988-05-17",
        },
      },
      sampleProfile(),
      new Set(["identity.birth_date"])
    );

    expect(payload.identity).toEqual({ birth_date: "1988-05-17" });
    expect(payload.identity.display_name).toBeUndefined();
    expect(payload.identity.email).toBeUndefined();
  });

  it("shows error toast on 403 for profile save", async () => {
    apiMock.put.mockRejectedValueOnce(Object.assign(new Error("Forbidden"), { status: 403 }));

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const identityCard = [...container.querySelectorAll(".profile-card")].find((card) =>
      card.querySelector(".profile-card-title")?.textContent.includes("Identity")
    );
    identityCard.querySelector(".profile-edit-link").click();
    identityCard.querySelector(".profile-inline-actions .btn-primary").click();
    await flushPromises();

    expect(toastMock).toHaveBeenCalledWith("Forbidden", "error");
  });

  it("buildPartialProfile whitelists only configured import identity keys", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      data: {
        type: "import-result",
        source: "github",
        imported: { identity: { birth_date: "1988-05-17" } },
      },
    }));

    const payload = buildPartialProfile(
      {
        identity: {
          display_name: "Injected",
          first_name: "Injected",
          last_name: "Injected",
          email: "injected@example.com",
          photo_url: "https://example.com/injected.jpg",
          company: "Injected Co",
          birth_date: "2000-01-01",
        },
      },
      sampleProfile(),
      new Set([
        "identity.display_name",
        "identity.first_name",
        "identity.last_name",
        "identity.email",
        "identity.photo_url",
        "identity.company",
        "identity.birth_date",
      ])
    );

    expect(payload.identity).toEqual({ birth_date: "2000-01-01" });
    expect(payload.identity.display_name).toBeUndefined();
    expect(payload.identity.first_name).toBeUndefined();
    expect(payload.identity.email).toBeUndefined();
  });
});
