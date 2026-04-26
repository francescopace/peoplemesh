import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let Auth;
let apiMock;

describe("AuthManager", () => {
  beforeEach(async () => {
    vi.resetModules();
    sessionStorage.clear();

    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
      setUnauthorizedHandler: vi.fn(),
    };
    vi.doMock("../assets/js/api.js", () => ({ api: apiMock }));
    vi.doMock("../config.js", () => ({
      Config: {
        apiBase: "https://test.app",
        providers: ["google", "microsoft", "github"],
      },
    }));

    const mod = await import("../assets/js/auth.js");
    Auth = mod.Auth;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("isAuthenticated returns false initially", () => {
    expect(Auth.isAuthenticated()).toBe(false);
  });

  it("getUser returns null initially", () => {
    expect(Auth.getUser()).toBeNull();
  });

  it("setUser stores user in sessionStorage", () => {
    const user = { id: "123", name: "Alice" };
    Auth.setUser(user);

    expect(Auth.getUser()).toEqual(user);
    expect(Auth.isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem("pm_user")).toBe(JSON.stringify(user));
  });

  it("setUser(null) clears user from sessionStorage", () => {
    Auth.setUser({ id: "123" });
    Auth.setUser(null);

    expect(Auth.getUser()).toBeNull();
    expect(Auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem("pm_user")).toBeNull();
  });

  it("init fetches /api/v1/auth/identity and sets user", async () => {
    const user = { id: "u1", name: "Bob" };
    apiMock.get.mockImplementation((path) => {
      if (path === "/api/v1/info") {
        return Promise.resolve({
          authProviders: { providers: ["google"], configured: ["google"] },
        });
      }
      if (path === "/api/v1/auth/identity") return Promise.resolve(user);
      return Promise.resolve(null);
    });

    await Auth.init();

    expect(Auth.isAuthenticated()).toBe(true);
    expect(Auth.getUser()).toEqual(user);
  });

  it("init sets user null when /api/v1/auth/identity fails", async () => {
    apiMock.get.mockImplementation((path) => {
      if (path === "/api/v1/info") {
        return Promise.resolve({
          authProviders: { providers: [], configured: [] },
        });
      }
      return Promise.reject(new Error("401"));
    });

    await Auth.init();

    expect(Auth.isAuthenticated()).toBe(false);
  });

  it("init consumes flat auth identity payload", async () => {
    apiMock.get.mockImplementation((path) => {
      if (path === "/api/v1/info") {
        return Promise.resolve({
          authProviders: { providers: ["google"], configured: ["google"] },
        });
      }
      return Promise.resolve({
        user_id: "u-1",
        provider: "google",
        entitlements: { is_admin: true },
        display_name: "Alice Admin",
        photo_url: "https://cdn.example.com/alice.png",
      });
    });

    await Auth.init();

    expect(Auth.getUser()).toEqual({
      user_id: "u-1",
      provider: "google",
      entitlements: { is_admin: true },
      display_name: "Alice Admin",
      photo_url: "https://cdn.example.com/alice.png",
    });
  });

  it("init is idempotent (only runs once)", async () => {
    apiMock.get.mockImplementation((path) => {
      if (path === "/api/v1/info") {
        return Promise.resolve({
          authProviders: { providers: [], configured: [] },
        });
      }
      return Promise.resolve({ id: "u1" });
    });

    await Auth.init();
    await Auth.init();

    expect(apiMock.get).toHaveBeenCalledTimes(2);
  });

  it("refreshProviders filters against Config.providers", async () => {
    apiMock.get.mockResolvedValue({
      authProviders: {
        providers: ["google", "unknown"],
        configured: ["google", "microsoft"],
      },
    });

    await Auth.refreshProviders();

    expect(Auth.getProviders()).toEqual(["google"]);
    expect(Auth.isProviderConfigured("google")).toBe(true);
    expect(Auth.isProviderConfigured("microsoft")).toBe(true);
    expect(Auth.isProviderConfigured("unknown")).toBe(false);
  });

  it("refreshProviders handles API failure gracefully", async () => {
    apiMock.get.mockRejectedValue(new Error("Network error"));

    await Auth.refreshProviders();

    expect(Auth.getProviders()).toEqual([]);
  });

  it("login sets location.href for valid provider", () => {
    Auth._providers = ["google"];
    Auth._configured = ["google"];

    const hrefSetter = vi.fn();
    delete window.location;
    window.location = { href: "", hash: "" };
    Object.defineProperty(window.location, "href", {
      set: hrefSetter,
      get: () => "",
    });

    Auth.login("google");

    expect(hrefSetter).toHaveBeenCalledWith("https://test.app/api/v1/auth/login/google");
  });

  it("login does not redirect for unknown provider", () => {
    Auth._providers = ["google"];
    Auth._configured = ["google"];
    const hrefSetter = vi.fn();
    delete window.location;
    window.location = { href: "", hash: "" };
    Object.defineProperty(window.location, "href", {
      set: hrefSetter,
      get: () => "",
    });

    Auth.login("unknown_provider");

    expect(hrefSetter).not.toHaveBeenCalled();
  });

  it("logout clears user and redirects to root", async () => {
    Auth.setUser({ id: "123" });
    apiMock.post.mockResolvedValue({});

    await Auth.logout();

    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/auth/logout");
    expect(Auth.isAuthenticated()).toBe(false);
    expect(Auth.getUser()).toBeNull();
    expect(window.location.hash).toBe("#/");
  });

  it("logout handles API error gracefully", async () => {
    Auth.setUser({ id: "123" });
    apiMock.post.mockRejectedValue(new Error("Network"));

    await Auth.logout();

    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/auth/logout");
    expect(Auth.isAuthenticated()).toBe(false);
  });
});
