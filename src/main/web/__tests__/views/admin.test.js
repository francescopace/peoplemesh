import { beforeEach, describe, expect, it, vi } from "vitest";

let renderAdmin;
let adminServiceMock;
let renderSkillsCatalogMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("admin view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/admin";

    adminServiceMock = {
      getSystemStatistics: vi.fn(),
    };
    renderSkillsCatalogMock = vi.fn(async (_container, options) => {
      options?.onCreateActionReady?.(() => {});
    });

    vi.doMock("../../assets/js/services/admin-service.js", () => adminServiceMock);
    vi.doMock("../../assets/js/components/skills-catalog-panel.js", () => ({
      renderSkillsCatalogPanel: renderSkillsCatalogMock,
    }));
  });

  it("shows permission empty state for non-admin users", async () => {
    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: { getUser: vi.fn(() => ({ entitlements: { is_admin: false } })) },
    }));

    const mod = await import("../../assets/js/views/admin.js");
    renderAdmin = mod.renderAdmin;

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderAdmin(container);
    await flushPromises();

    expect(container.textContent).toContain("You do not have permission to access this page.");
  });

  it("renders overview stats for admin users", async () => {
    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: { getUser: vi.fn(() => ({ entitlements: { is_admin: true } })) },
    }));
    adminServiceMock.getSystemStatistics.mockResolvedValue({
      users: 10,
      jobs: 5,
      groups: 3,
      skills: 99,
      timings: {},
    });

    const mod = await import("../../assets/js/views/admin.js");
    renderAdmin = mod.renderAdmin;

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderAdmin(container);
    await flushPromises();

    expect(renderSkillsCatalogMock).toHaveBeenCalled();
    expect(container.textContent).toContain("Data Overview");
    expect(container.textContent).toContain("10");
    expect(container.textContent).toContain("99");
  });
});
