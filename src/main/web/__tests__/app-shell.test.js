import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

let renderAppShell;
let updateNavActive;
let authMock;
let deriveInitialsMock;
let getOrganizationNameMock;
let renderBrandMock;

describe("app-shell", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = '<div id="root"></div>';

    authMock = {
      getUser: vi.fn().mockReturnValue({ display_name: "John Doe", entitlements: {} }),
      logout: vi.fn(),
    };
    deriveInitialsMock = vi.fn().mockReturnValue("JD");
    getOrganizationNameMock = vi.fn().mockResolvedValue(null);
    renderBrandMock = vi.fn().mockReturnValue('<span class="brand">PeopleMesh</span>');

    vi.doMock("../assets/js/auth.js", () => ({ Auth: authMock }));
    vi.doMock("../assets/js/brand.js", () => ({
      renderBrand: renderBrandMock,
    }));
    vi.doMock("../assets/js/footer.js", () => ({
      renderFooter: vi.fn().mockReturnValue('<footer class="footer">Footer</footer>'),
    }));
    vi.doMock("../assets/js/utils/initials.js", () => ({
      deriveInitials: deriveInitialsMock,
    }));
    vi.doMock("../assets/js/platform-info.js", () => ({
      getOrganizationName: getOrganizationNameMock,
    }));

    const mod = await import("../assets/js/app-shell.js");
    renderAppShell = mod.renderAppShell;
    updateNavActive = mod.updateNavActive;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  it("renders shell with initials avatar fallback", async () => {
    const root = document.getElementById("root");

    await renderAppShell(root);

    expect(root.querySelector(".app-layout")).not.toBeNull();
    expect(root.querySelector(".user-avatar").textContent).toBe("JD");
    expect(root.querySelectorAll(".header-nav-link[data-path]").length).toBe(2);
    expect(root.querySelector('.header-nav-link[data-path="/search"] .fa-magnifying-glass')).not.toBeNull();
    expect(root.querySelector('.header-nav-link[data-path="/my-mesh"] .fa-users')).not.toBeNull();
    expect(root.querySelector('.header-nav-link[data-path="/my-mesh"]')?.getAttribute("data-tooltip"))
      .toBe("Discover what matches your profile.");
    expect(renderBrandMock).toHaveBeenCalledWith(expect.objectContaining({
      text: "PeopleMesh",
      organizationName: null,
    }));
  });

  it("renders organization name in the top brand when available", async () => {
    const root = document.getElementById("root");
    getOrganizationNameMock.mockResolvedValue("Acme Corp");

    await renderAppShell(root);

    expect(renderBrandMock).toHaveBeenCalledWith(expect.objectContaining({
      text: "PeopleMesh",
      organizationName: "Acme Corp",
      organizationClass: "app-brand-organization",
      organizationSeparator: "| ",
    }));
  });

  it("renders profile image when a valid remote photo is available", async () => {
    const root = document.getElementById("root");
    authMock.getUser.mockReturnValue({
      display_name: "John Doe",
      photo_url: "https://cdn.example.com/photo.png",
      entitlements: {},
    });

    await renderAppShell(root);

    const img = root.querySelector(".user-avatar--img");
    expect(img).not.toBeNull();
    expect(img.getAttribute("src")).toBe("https://cdn.example.com/photo.png");
    expect(img.getAttribute("alt")).toBe("JD");
  });

  it("adds admin navigation entry for admin users", async () => {
    const root = document.getElementById("root");
    authMock.getUser.mockReturnValue({
      name: "Jane Admin",
      entitlements: { is_admin: true },
    });

    await renderAppShell(root);

    expect(root.querySelector('.header-nav-link[data-path="/admin"]')).not.toBeNull();
    expect(root.querySelector('.header-nav-link[data-path="/admin"] .fa-user-shield')).not.toBeNull();
  });

  it("executes logout when logout button is clicked", async () => {
    const root = document.getElementById("root");
    await renderAppShell(root);

    root.querySelector("#dropdown-logout-btn").click();

    expect(authMock.logout).toHaveBeenCalledTimes(1);
  });

  it("toggles dropdown open and closes on outside click", async () => {
    const root = document.getElementById("root");
    await renderAppShell(root);

    const btn = root.querySelector("#profile-menu-btn");
    const dropdown = root.querySelector("#profile-dropdown");

    btn.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(dropdown.classList.contains("open")).toBe(true);
    expect(btn.getAttribute("aria-expanded")).toBe("true");

    document.body.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(dropdown.classList.contains("open")).toBe(false);
    expect(btn.getAttribute("aria-expanded")).toBe("false");
  });

  it("keeps one layout and clears main content on re-render", async () => {
    const root = document.getElementById("root");
    await renderAppShell(root);
    const main = root.querySelector(".app-main");
    main.innerHTML = "<p>stale</p>";

    await renderAppShell(root);

    expect(root.querySelectorAll(".app-layout").length).toBe(1);
    expect(root.querySelector(".app-main").innerHTML).toBe("");
  });

  it("updates active nav item including my-mesh aliases", async () => {
    document.body.innerHTML = `
      <a class="header-nav-link" data-path="/search"></a>
      <a class="header-nav-link" data-path="/my-mesh"></a>
      <a class="mobile-bottom-nav-item" data-path="/search"></a>
      <a class="mobile-bottom-nav-item" data-path="/my-mesh"></a>
    `;

    updateNavActive("/jobs/42");

    const myMeshLinks = [...document.querySelectorAll('[data-path="/my-mesh"]')];
    const searchLinks = [...document.querySelectorAll('[data-path="/search"]')];
    expect(myMeshLinks.every((link) => link.classList.contains("active"))).toBe(true);
    expect(searchLinks.every((link) => !link.classList.contains("active"))).toBe(true);
  });
});
