import { beforeEach, describe, expect, it, vi } from "vitest";

let renderExplore;
let matchesServiceMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("explore view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/my-mesh";

    matchesServiceMock = {
      fetchMyMeshMatches: vi.fn(),
    };

    vi.doMock("../../assets/js/services/matches-service.js", () => matchesServiceMock);
    vi.doMock("../../assets/js/contact-actions.js", () => ({
      contactFooter: () => null,
    }));

    const mod = await import("../../assets/js/views/explore.js");
    renderExplore = mod.renderExplore;
  });

  it("renders cards on happy path", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "p-1",
        nodeType: "PEOPLE",
        title: "Alice",
        score: 0.88,
        person: {
          city: "Milan",
          roles: ["Developer"],
          skillsTechnical: ["Java"],
        },
        country: "IT",
        tags: ["Java"],
        breakdown: {
          commonItems: ["Java"],
          geographyReason: "same_country",
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    expect(matchesServiceMock.fetchMyMeshMatches).toHaveBeenCalledWith({
      limit: 9,
      offset: 0,
      type: "PEOPLE",
    });
    expect(container.querySelectorAll(".discover-card").length).toBe(1);
  });

  it("shows profile guidance empty state on 404", async () => {
    const err = new Error("Not Found");
    err.status = 404;
    matchesServiceMock.fetchMyMeshMatches.mockRejectedValue(err);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    expect(container.textContent).toContain("To find better matches, update your");
  });
});
