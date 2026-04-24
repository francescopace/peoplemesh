import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";

let renderAppShell;
let updateNavActive;
let authMock;
let getMyProfileInFlightMock;
let deriveInitialsMock;

describe("app-shell", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = '<div id="root"></div>';

    authMock = {
      getUser: vi.fn().mockReturnValue({ name: "John Doe", entitlements: {} }),
      logout: vi.fn(),
    };
    getMyProfileInFlightMock = vi.fn().mockResolvedValue({});
    deriveInitialsMock = vi.fn().mockReturnValue("JD");

    vi.doMock("../assets/js/auth.js", () => ({ Auth: authMock }));
    vi.doMock("../assets/js/brand.js", () => ({
      renderBrand: vi.fn().mockReturnValue('<span class="brand">PeopleMesh</span>'),
    }));
    vi.doMock("../assets/js/footer.js", () => ({
      renderFooter: vi.fn().mockReturnValue('<footer class="footer">Footer</footer>'),
    }));
    vi.doMock("../assets/js/services/profile-service.js", () => ({
      getMyProfileInFlight: getMyProfileInFlightMock,
    }));
    vi.doMock("../assets/js/utils/initials.js", () => ({
      deriveInitials: deriveInitialsMock,
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
  });

  it("renders profile image when a valid remote photo is available", async () => {
    const root = document.getElementById("root");
    getMyProfileInFlightMock.mockResolvedValue({
      identity: { photo_url: "https://cdn.example.com/photo.png" },
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
