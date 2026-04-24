import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let showLoginModal;
let authMock;

describe("showLoginModal()", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";

    authMock = {
      getProviders: vi.fn().mockReturnValue(["google", "microsoft"]),
      login: vi.fn(),
    };

    vi.doMock("../assets/js/auth.js", () => ({ Auth: authMock }));

    const mod = await import("../assets/js/login-modal.js");
    showLoginModal = mod.showLoginModal;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  it("appends overlay to document body", () => {
    showLoginModal();
    const overlay = document.getElementById("login-modal-overlay");
    expect(overlay).not.toBeNull();
    expect(overlay.className).toContain("login-overlay");
  });

  it("renders provider buttons", () => {
    showLoginModal();
    const buttons = document.querySelectorAll(".login-provider-btn");
    expect(buttons.length).toBe(2);
  });

  it("redirects directly when only one provider is available", () => {
    authMock.getProviders.mockReturnValue(["google"]);
    showLoginModal();
    expect(authMock.login).toHaveBeenCalledWith("google");
    expect(document.getElementById("login-modal-overlay")).toBeNull();
  });

  it("shows provider labels", () => {
    showLoginModal();
    const overlay = document.getElementById("login-modal-overlay");
    expect(overlay.textContent).toContain("Continue with Google");
    expect(overlay.textContent).toContain("Continue with Microsoft");
  });

  it("calls Auth.login when provider button is clicked", () => {
    showLoginModal();
    const btn = document.querySelector('[data-provider="google"]');
    btn.click();
    expect(authMock.login).toHaveBeenCalledWith("google");
  });

  it("disables button and adds loading class on click", () => {
    showLoginModal();
    const btn = document.querySelector('[data-provider="google"]');
    btn.click();
    expect(btn.disabled).toBe(true);
    expect(btn.className).toContain("login-provider-loading");
  });

  it("removes existing modal before creating new one", () => {
    showLoginModal();
    showLoginModal();
    const overlays = document.querySelectorAll("#login-modal-overlay");
    expect(overlays.length).toBe(1);
  });

  it("contains terms and privacy links", () => {
    showLoginModal();
    const overlay = document.getElementById("login-modal-overlay");
    expect(overlay.innerHTML).toContain('href="#/terms_of_service"');
    expect(overlay.innerHTML).toContain('href="#/privacy_policy"');
  });

  it("shows empty message when no providers", async () => {
    authMock.getProviders.mockReturnValue([]);
    showLoginModal();
    const overlay = document.getElementById("login-modal-overlay");
    expect(overlay.textContent).toContain("No login provider is configured");
  });

  it("includes close button", () => {
    showLoginModal();
    const closeBtn = document.querySelector(".login-close");
    expect(closeBtn).not.toBeNull();
    expect(closeBtn.getAttribute("aria-label")).toBe("Close");
  });

  it("has dialog role and aria-modal", () => {
    showLoginModal();
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog.getAttribute("aria-modal")).toBe("true");
  });

  it("focuses the first available control when opened", () => {
    showLoginModal();
    const closeBtn = document.querySelector(".login-close");
    expect(document.activeElement === closeBtn || document.activeElement?.classList.contains("login-provider-btn")).toBe(true);
  });

  it("closes on Escape key", () => {
    vi.useFakeTimers();
    showLoginModal();
    const overlay = document.getElementById("login-modal-overlay");
    overlay.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    vi.advanceTimersByTime(300);
    expect(document.getElementById("login-modal-overlay")).toBeNull();
    vi.useRealTimers();
  });
});
