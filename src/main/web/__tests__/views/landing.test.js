import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let renderLanding;
let getOrganizationNameMock;
let authMock;
let renderTopBarUserMenuMock;
let bindTopBarUserMenuMock;

describe("renderLanding()", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    localStorage.clear();
    window.location.hash = "";
    getOrganizationNameMock = vi.fn().mockResolvedValue(null);
    authMock = {
      isAuthenticated: vi.fn().mockReturnValue(false),
      getProviders: vi.fn().mockReturnValue(["google"]),
      login: vi.fn(),
      getUser: vi.fn().mockReturnValue({ name: "John Doe", entitlements: {} }),
    };
    renderTopBarUserMenuMock = vi.fn().mockResolvedValue('<div class="profile-dropdown-wrap" id="profile-dropdown-wrap"></div>');
    bindTopBarUserMenuMock = vi.fn();

    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: authMock,
    }));
    vi.doMock("../../assets/js/login-modal.js", () => ({
      showLoginModal: vi.fn(),
    }));
    vi.doMock("../../assets/js/platform-info.js", () => ({
      getOrganizationName: getOrganizationNameMock,
    }));
    vi.doMock("../../assets/js/top-bar-user-menu.js", () => ({
      renderTopBarUserMenu: renderTopBarUserMenuMock,
      bindTopBarUserMenu: bindTopBarUserMenuMock,
    }));

    const mod = await import("../../assets/js/views/landing.js");
    renderLanding = mod.renderLanding;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("renders hero section", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector("#section-hero")).not.toBeNull();
    expect(container.querySelector("h1").textContent).toContain("mesh");
  });

  it("renders search input", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const input = container.querySelector("#landing-search-input");
    expect(input).not.toBeNull();
    expect(input.getAttribute("type")).toBe("text");
  });

  it("renders example query chips", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const chips = container.querySelectorAll(".landing-example-chip");
    expect(chips.length).toBeGreaterThan(0);
  });

  it("renders why section", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector("#section-why")).not.toBeNull();
    expect(container.textContent).toContain("Why PeopleMesh");
  });

  it("renders feature cards", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const cards = container.querySelectorAll(".feature-card");
    expect(cards.length).toBe(4);
  });

  it("shows sign in button when not authenticated", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const btn = container.querySelector("#nav-action-btn");
    expect(btn.textContent).toContain("Sign In");
  });

  it("renders navigation with brand", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector("#landing-nav")).not.toBeNull();
    expect(container.textContent).toContain("PeopleMesh");
  });

  it("renders section navigation dots", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const dots = container.querySelectorAll(".section-dot");
    expect(dots.length).toBe(2);
  });

  it("renders footer", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("shows cookie banner when not acknowledged", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector(".cookie-banner")).not.toBeNull();
  });

  it("hides cookie banner after acknowledgement", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    container.querySelector("#cookie-ack-btn").click();

    expect(container.querySelector(".cookie-banner")).toBeNull();
    expect(localStorage.getItem("pm_cookie_ack")).toBe("1");
  });

  it("does not show cookie banner if previously acknowledged", async () => {
    localStorage.setItem("pm_cookie_ack", "1");
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector(".cookie-banner")).toBeNull();
  });

  it("clicking example chip fills search input", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    const chip = container.querySelector(".landing-example-chip");
    const input = container.querySelector("#landing-search-input");
    chip.click();

    expect(input.value).toBe(chip.dataset.query);
  });

  it("renders organization name next to PeopleMesh when available", async () => {
    getOrganizationNameMock.mockResolvedValue("Acme Corp");
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector(".nav-logo-organization")?.textContent).toBe("| Acme Corp");
  });

  it("shows authenticated top bar user menu when user is logged in", async () => {
    authMock.isAuthenticated.mockReturnValue(true);
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector("#profile-dropdown-wrap")).not.toBeNull();
    expect(container.querySelector("#nav-action-btn")).toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/search"]')).not.toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/my-mesh"]')).not.toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/search"] .fa-magnifying-glass')).not.toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/my-mesh"] .fa-users')).not.toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/my-mesh"]')?.getAttribute("data-tooltip"))
      .toBe("Discover what matches your profile.");
    expect(container.querySelector('.header-nav-link[data-path="/admin"]')).toBeNull();
    expect(bindTopBarUserMenuMock).toHaveBeenCalledWith(container);
  });

  it("shows admin link in landing top bar for admin users", async () => {
    authMock.isAuthenticated.mockReturnValue(true);
    authMock.getUser.mockReturnValue({
      name: "Jane Admin",
      entitlements: { is_admin: true },
    });
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderLanding(container);

    expect(container.querySelector('.header-nav-link[data-path="/admin"]')).not.toBeNull();
    expect(container.querySelector('.header-nav-link[data-path="/admin"] .fa-user-shield')).not.toBeNull();
  });
});
