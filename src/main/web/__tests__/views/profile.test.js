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
      patch: vi.fn().mockResolvedValue({}),
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

    identityCard.querySelector('[data-profile-field="birthDate"] .profile-field-value--interactive').click();

    expect(identityCard.querySelector('[data-profile-field="birthDate"] input[type="date"]')).not.toBeNull();
    const identityInputs = [...identityCard.querySelectorAll("input")].filter((inp) => inp.type !== "date");
    expect(identityInputs.length).toBe(0);
  });

  it("sends only identity.birth_date in /api/v1/me update payload", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const identityCard = [...container.querySelectorAll(".profile-card")].find((card) =>
      card.querySelector(".profile-card-title")?.textContent.includes("Identity")
    );
    identityCard.querySelector('[data-profile-field="birthDate"] .profile-field-value--interactive').click();

    const birthInput = identityCard.querySelector('[data-profile-field="birthDate"] input[type="date"]');
    birthInput.value = "1992-04-12";
    birthInput.dispatchEvent(new Event("blur", { bubbles: true }));
    await flushPromises();

    expect(apiMock.patch).toHaveBeenCalled();
    const [url, payload] = apiMock.patch.mock.calls[0];
    expect(url).toBe("/api/v1/me");
    expect(payload.identity).toEqual({ birth_date: "1992-04-12" });
    expect(payload.identity.display_name).toBeUndefined();
    expect(payload.identity.email).toBeUndefined();
    const consentCalls = apiMock.get.mock.calls.filter(([path]) => path === "/api/v1/me/consents");
    expect(consentCalls).toHaveLength(1);
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
    apiMock.patch.mockRejectedValueOnce(Object.assign(new Error("Forbidden"), { status: 403 }));

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const identityCard = [...container.querySelectorAll(".profile-card")].find((card) =>
      card.querySelector(".profile-card-title")?.textContent.includes("Identity")
    );
    identityCard.querySelector('[data-profile-field="birthDate"] .profile-field-value--interactive').click();
    const birthInput403 = identityCard.querySelector('[data-profile-field="birthDate"] input[type="date"]');
    birthInput403.value = "1992-04-12";
    birthInput403.dispatchEvent(new Event("blur", { bubbles: true }));
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

  it("removes a technical skill directly from tags in view mode", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const removeBtn = container.querySelector('.profile-tag-remove[aria-label="Remove Java"]');
    expect(removeBtn).not.toBeNull();
    removeBtn.click();
    await flushPromises();
    await flushPromises();

    expect(apiMock.patch).toHaveBeenCalled();
    const [, payload] = apiMock.patch.mock.calls[0];
    expect(payload.professional.skills_technical).toEqual([]);
  });

  it("adds a technical skill directly from tags in view mode", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const technicalGroup = [...container.querySelectorAll(".profile-tag-group")].find((group) =>
      group.querySelector(".profile-field-label")?.textContent.includes("Technical Skills")
    );
    expect(technicalGroup).toBeTruthy();

    const addInput = technicalGroup.querySelector(".profile-tag-add-input");
    expect(addInput).not.toBeNull();
    addInput.value = "Kotlin";
    addInput.dispatchEvent(new Event("input"));
    addInput.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await flushPromises();
    await flushPromises();

    expect(apiMock.patch).toHaveBeenCalled();
    const [, payload] = apiMock.patch.mock.calls[0];
    expect(payload.professional.skills_technical).toEqual(["Java", "Kotlin"]);
  });

  it("keeps focus on add input after add and remove without reloading profile", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const technicalGroup = [...container.querySelectorAll(".profile-tag-group")].find((group) =>
      group.querySelector(".profile-field-label")?.textContent.includes("Technical Skills")
    );
    expect(technicalGroup).toBeTruthy();

    const addInput = technicalGroup.querySelector(".profile-tag-add-input");
    expect(addInput).not.toBeNull();
    addInput.focus();
    addInput.value = "Kotlin";
    addInput.dispatchEvent(new Event("input"));
    addInput.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await flushPromises();
    await flushPromises();

    expect(document.activeElement).toBe(addInput);

    const removeBtn = technicalGroup.querySelector('.profile-tag-remove[aria-label="Remove Kotlin"]');
    expect(removeBtn).not.toBeNull();
    removeBtn.click();
    await flushPromises();
    await flushPromises();

    expect(document.activeElement).toBe(addInput);
    const meCalls = apiMock.get.mock.calls.filter(([path]) => path === "/api/v1/me");
    expect(meCalls).toHaveLength(1);
  });

  it("auto-adds skill when selecting an autocomplete suggestion", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderProfile(container);

    const technicalGroup = [...container.querySelectorAll(".profile-tag-group")].find((group) =>
      group.querySelector(".profile-field-label")?.textContent.includes("Technical Skills")
    );
    expect(technicalGroup).toBeTruthy();

    const addInput = technicalGroup.querySelector(".profile-tag-add-input");
    expect(addInput).not.toBeNull();
    const listId = addInput.getAttribute("list");
    expect(listId).toBeTruthy();
    const dataList = document.getElementById(listId);
    expect(dataList).not.toBeNull();
    const option = document.createElement("option");
    option.value = "Kotlin";
    dataList.appendChild(option);

    addInput.value = "Kotlin";
    addInput.dispatchEvent(new Event("change", { bubbles: true }));
    await flushPromises();
    await flushPromises();

    expect(apiMock.patch).toHaveBeenCalled();
    const [, payload] = apiMock.patch.mock.calls[0];
    expect(payload.professional.skills_technical).toEqual(["Java", "Kotlin"]);
  });
});
