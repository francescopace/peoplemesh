import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let renderLanding;

describe("renderLanding()", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    localStorage.clear();
    window.location.hash = "";

    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: {
        isAuthenticated: vi.fn().mockReturnValue(false),
        getProviders: vi.fn().mockReturnValue(["google"]),
        login: vi.fn(),
      },
    }));
    vi.doMock("../../assets/js/login-modal.js", () => ({
      showLoginModal: vi.fn(),
    }));

    const mod = await import("../../assets/js/views/landing.js");
    renderLanding = mod.renderLanding;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("renders hero section", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector("#section-hero")).not.toBeNull();
    expect(container.querySelector("h1").textContent).toContain("mesh");
  });

  it("renders search input", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const input = container.querySelector("#landing-search-input");
    expect(input).not.toBeNull();
    expect(input.getAttribute("type")).toBe("text");
  });

  it("renders example query chips", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const chips = container.querySelectorAll(".landing-example-chip");
    expect(chips.length).toBeGreaterThan(0);
  });

  it("renders why section", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector("#section-why")).not.toBeNull();
    expect(container.textContent).toContain("Why PeopleMesh");
  });

  it("renders feature cards", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const cards = container.querySelectorAll(".feature-card");
    expect(cards.length).toBe(4);
  });

  it("shows sign in button when not authenticated", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const btn = container.querySelector("#nav-action-btn");
    expect(btn.textContent).toContain("Sign In");
  });

  it("renders navigation with brand", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector("#landing-nav")).not.toBeNull();
    expect(container.textContent).toContain("PeopleMesh");
  });

  it("renders section navigation dots", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const dots = container.querySelectorAll(".section-dot");
    expect(dots.length).toBe(2);
  });

  it("renders footer", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector("footer")).not.toBeNull();
  });

  it("shows cookie banner when not acknowledged", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector(".cookie-banner")).not.toBeNull();
  });

  it("hides cookie banner after acknowledgement", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    container.querySelector("#cookie-ack-btn").click();

    expect(container.querySelector(".cookie-banner")).toBeNull();
    expect(localStorage.getItem("pm_cookie_ack")).toBe("1");
  });

  it("does not show cookie banner if previously acknowledged", () => {
    localStorage.setItem("pm_cookie_ack", "1");
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    expect(container.querySelector(".cookie-banner")).toBeNull();
  });

  it("clicking example chip fills search input", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    renderLanding(container);

    const chip = container.querySelector(".landing-example-chip");
    const input = container.querySelector("#landing-search-input");
    chip.click();

    expect(input.value).toBe(chip.dataset.query);
  });
});
