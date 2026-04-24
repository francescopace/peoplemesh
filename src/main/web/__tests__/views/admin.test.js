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
    renderSkillsCatalogMock = vi.fn(async () => {});

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
      others: 3,
      skills: 99,
      timings: {},
      searchableNodes: 10,
      searchableNodesWithEmbedding: 10,
    });

    const mod = await import("../../assets/js/views/admin.js");
    renderAdmin = mod.renderAdmin;

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderAdmin(container);
    await flushPromises();

    expect(renderSkillsCatalogMock).not.toHaveBeenCalled();
    expect(container.textContent).toContain("Data Overview");
    expect(container.textContent).toContain("Inference Statistics");
    expect(container.textContent).toContain("Embedding Status");
    expect(container.textContent).toContain("100.00%");
    expect(container.textContent).toContain("10");
    expect(container.textContent).toContain("99");

    const skillsCard = container.querySelector("[aria-label='Open skills list']");
    expect(skillsCard).toBeTruthy();
    expect(skillsCard.textContent).toContain("Click to open the skills dictionary");
    skillsCard.click();
    await flushPromises();

    expect(renderSkillsCatalogMock).toHaveBeenCalledTimes(1);
    const [, panelOptions] = renderSkillsCatalogMock.mock.calls[0];
    expect(panelOptions).toEqual(expect.objectContaining({
      clearContainer: true,
      wrapInCard: false,
      actionsContainer: expect.any(HTMLElement),
    }));
  });

  it("marks embedding coverage in red when below 100%", async () => {
    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: { getUser: vi.fn(() => ({ entitlements: { is_admin: true } })) },
    }));
    adminServiceMock.getSystemStatistics.mockResolvedValue({
      users: 1,
      jobs: 1,
      others: 1,
      skills: 1,
      timings: {},
      searchableNodes: 10,
      searchableNodesWithEmbedding: 8,
    });

    const mod = await import("../../assets/js/views/admin.js");
    renderAdmin = mod.renderAdmin;

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderAdmin(container);
    await flushPromises();

    const coverageValue = Array.from(container.querySelectorAll("p")).find((el) => el.textContent?.includes("80.00%"));
    expect(coverageValue).toBeTruthy();
    expect(coverageValue.classList.contains("text-danger")).toBe(true);
  });
});
