import { beforeEach, describe, expect, it, vi } from "vitest";

let renderSkillsCatalogPanel;
let listSkillsMock;
let importSkillsCsvMock;
let cleanupUnusedSkillsMock;
let toastMock;
let authGetUserMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("skills-catalog-panel", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    vi.clearAllMocks();

    listSkillsMock = vi.fn();
    importSkillsCsvMock = vi.fn();
    cleanupUnusedSkillsMock = vi.fn();
    toastMock = vi.fn();
    authGetUserMock = vi.fn(() => ({ entitlements: { is_admin: true } }));

    vi.doMock("../../assets/js/auth.js", () => ({
      Auth: { getUser: authGetUserMock },
    }));
    vi.doMock("../../assets/js/services/skills-service.js", () => ({
      listSkills: listSkillsMock,
      importSkillsCsv: importSkillsCsvMock,
      cleanupUnusedSkills: cleanupUnusedSkillsMock,
    }));
    vi.doMock("../../assets/js/ui.js", async () => {
      const actual = await vi.importActual("../../assets/js/ui.js");
      return {
        ...actual,
        toast: toastMock,
      };
    });

    ({ renderSkillsCatalogPanel } = await import("../../assets/js/components/skills-catalog-panel.js"));
  });

  it("renders global skills table", async () => {
    listSkillsMock.mockResolvedValueOnce([
      { id: "s1", name: "java", aliases: ["Java"], usageCount: 2 },
      { id: "s2", name: "kubernetes", aliases: ["K8s"], usageCount: 1 },
    ]);

    const container = document.createElement("section");
    document.body.appendChild(container);

    await renderSkillsCatalogPanel(container);

    expect(container.textContent).toContain("java");
    expect(container.textContent).toContain("kubernetes");
    expect(listSkillsMock).toHaveBeenCalledWith({ page: 0, size: 50 });
  });

  it("hides admin actions for non-admin users", async () => {
    authGetUserMock.mockReturnValueOnce({ entitlements: { is_admin: false } });
    listSkillsMock.mockResolvedValueOnce([]);

    const container = document.createElement("section");
    document.body.appendChild(container);
    await renderSkillsCatalogPanel(container);

    expect(container.textContent).not.toContain("Upload");
    expect(container.textContent).not.toContain("Purge unused");
  });

  it("imports CSV and refreshes list", async () => {
    listSkillsMock
      .mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }])
      .mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }]);
    importSkillsCsvMock.mockResolvedValue({ imported: 1 });

    const container = document.createElement("section");
    document.body.appendChild(container);
    await renderSkillsCatalogPanel(container);

    const input = container.querySelector("input[type='file']");
    const importButton = Array.from(container.querySelectorAll("button"))
      .find((btn) => btn.textContent.includes("Upload"));
    expect(importButton).toBeTruthy();
    const file = new File(["name\nJava"], "skills.csv", { type: "text/csv" });
    Object.defineProperty(input, "files", { value: [file], configurable: true });
    input.dispatchEvent(new Event("change"));
    await flushPromises();
    await flushPromises();

    expect(importSkillsCsvMock).toHaveBeenCalledTimes(1);
    expect(toastMock).toHaveBeenCalledWith("Imported 1 skills", "success");
  });

  it("cleanup action calls cleanup endpoint", async () => {
    listSkillsMock
      .mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }])
      .mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }]);
    cleanupUnusedSkillsMock.mockResolvedValue({ deleted: 3 });
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

    const container = document.createElement("section");
    document.body.appendChild(container);
    await renderSkillsCatalogPanel(container);
    const cleanupButton = Array.from(container.querySelectorAll("button"))
      .find((btn) => btn.textContent.includes("Purge unused"));
    cleanupButton.click();
    await flushPromises();
    await flushPromises();

    expect(cleanupUnusedSkillsMock).toHaveBeenCalledTimes(1);
    expect(toastMock).toHaveBeenCalledWith("Deleted 3 unused skills", "success");
    confirmSpy.mockRestore();
  });

  it("renders actions into external header container for modal usage", async () => {
    listSkillsMock.mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }]);

    const container = document.createElement("section");
    const headerActions = document.createElement("div");
    document.body.appendChild(container);

    await renderSkillsCatalogPanel(container, {
      wrapInCard: false,
      actionsContainer: headerActions,
    });

    expect(container.querySelector(".skills-global-panel")).toBeNull();
    expect(headerActions.textContent).toContain("Upload");
    expect(headerActions.textContent).toContain("Purge unused");
    expect(container.querySelector(".skills-list-footer input[aria-label='Search skills']")).toBeTruthy();
  });

  it("loads more skills when clicking Load more", async () => {
    const firstPage = Array.from({ length: 50 }, (_, i) => ({
      id: `s-${i}`,
      name: `skill-${i}`,
      aliases: [],
      usageCount: i,
    }));
    listSkillsMock
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce([{ id: "s-51", name: "skill-51", aliases: [], usageCount: 1 }]);

    const container = document.createElement("section");
    document.body.appendChild(container);
    await renderSkillsCatalogPanel(container);

    const loadMoreButton = Array.from(container.querySelectorAll("button"))
      .find((btn) => btn.textContent.includes("Load more"));
    expect(loadMoreButton).toBeTruthy();
    loadMoreButton.click();
    await flushPromises();

    expect(listSkillsMock).toHaveBeenNthCalledWith(2, { page: 1, size: 50 });
    expect(container.textContent).toContain("skill-51");
  });

  it("filters skills using search query", async () => {
    vi.useFakeTimers();
    listSkillsMock
      .mockResolvedValueOnce([{ id: "s1", name: "java", aliases: ["Java"], usageCount: 1 }])
      .mockResolvedValueOnce([{ id: "s2", name: "javascript", aliases: ["JS"], usageCount: 1 }]);

    const container = document.createElement("section");
    document.body.appendChild(container);
    await renderSkillsCatalogPanel(container);

    const searchInput = container.querySelector("input[aria-label='Search skills']");
    searchInput.value = "java";
    searchInput.dispatchEvent(new Event("input"));
    await vi.advanceTimersByTimeAsync(320);

    expect(listSkillsMock).toHaveBeenNthCalledWith(2, { page: 0, size: 50, q: "java" });
    expect(container.textContent).toContain("javascript");
    vi.useRealTimers();
  });
});
