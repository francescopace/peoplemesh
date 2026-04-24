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

  it("shows matched common items even when not present in visible tags", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "p-2",
        nodeType: "PEOPLE",
        title: "Bob",
        score: 0.82,
        person: { city: "Rome", roles: ["Developer"] },
        country: "IT",
        tags: ["Java"],
        breakdown: {
          commonItems: ["Kubernetes"],
          geographyReason: "different_region",
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    const tagLabels = [...container.querySelectorAll(".dc-tag")].map((el) => el.textContent.trim());
    expect(tagLabels).toContain("Java");
    expect(tagLabels).toContain("Kubernetes");
  });

  it("renders match explainer tooltip and hidden-tags indicator", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "p-3",
        nodeType: "PEOPLE",
        title: "Carla",
        score: 0.91,
        person: { city: "Turin", roles: ["Engineer"] },
        country: "IT",
        tags: ["Java", "Spring", "Kafka", "Docker", "Kubernetes", "Postgres", "AWS", "Redis", "GraphQL"],
        breakdown: {
          embeddingScore: 0.9,
          overlapScore: 1.0,
          niceToHaveScore: 0.5,
          geographyScore: 1.0,
          finalScore: 0.91,
          seniorityScore: 1.0,
          reasonCodes: ["SEMANTIC_SIMILARITY", "MUST_HAVE_SKILLS", "SENIORITY_MATCH"],
          matchedMustHaveSkills: ["Java", "Spring"],
          missingMustHaveSkills: ["Go"],
          commonItems: ["Java", "Spring"],
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    expect(container.querySelector(".dc-match-tooltip")).not.toBeNull();
    expect(container.textContent).toContain("+1 more tags");
    expect(container.textContent).toContain("Score points");
    expect(container.textContent).toContain("Seniority:");
    expect(container.textContent).toContain("Total points:");
  });

  it("shows keyword contribution for non-people cards", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "job-1",
        nodeType: "JOB",
        title: "Platform Engineer",
        description: "Build internal platform tooling",
        score: 0.63,
        country: "IT",
        tags: ["Platform", "Kubernetes"],
        breakdown: {
          embeddingScore: 0.8,
          overlapScore: 0.5,
          niceToHaveScore: 0.4,
          geographyScore: 0,
          finalScore: 0.63,
          keywordScore: 0.9,
          commonItems: ["Platform"],
          matchedMustHaveSkills: ["Platform"],
          matchedNiceToHaveSkills: ["Kubernetes"],
          weightEmbedding: 0.5,
          weightMustHave: 0.2,
          weightNiceToHave: 0.1,
          weightLanguage: 0,
          weightIndustry: 0,
          weightGeography: 0.1,
          weightSeniority: 0,
          weightKeyword: 0.1,
          mustHaveRequested: true,
          niceToHaveRequested: true,
          geographyRequested: false,
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    const tooltip = container.querySelector(".dc-match-tooltip");
    expect(tooltip).toBeTruthy();
    expect(tooltip.textContent).toContain("Keywords:");
    expect(tooltip.textContent).not.toContain("Other scoring factors:");
  });

  it("prioritizes matched tags before non-matched ones", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "p-4",
        nodeType: "PEOPLE",
        title: "Diego",
        score: 0.79,
        person: { city: "Naples", roles: ["Engineer"] },
        country: "IT",
        tags: ["Terraform", "React", "Java", "Docker"],
        breakdown: {
          commonItems: ["Java", "Docker"],
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    const renderedTags = [...container.querySelectorAll(".dc-tag")]
      .map((tag) => tag.textContent.trim())
      .filter((tag) => !tag.includes("more tags"));

    expect(renderedTags.slice(0, 2)).toEqual(["Java", "Docker"]);
  });

  it("shows skill level badge when provided on person details", async () => {
    matchesServiceMock.fetchMyMeshMatches.mockResolvedValue([
      {
        id: "p-5",
        nodeType: "PEOPLE",
        title: "Elena",
        score: 0.83,
        person: {
          city: "Bologna",
          roles: ["Engineer"],
          skillLevels: { Java: 4 },
        },
        country: "IT",
        tags: ["Java"],
        breakdown: {
          commonItems: ["Java"],
        },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderExplore(container);
    await flushPromises();

    expect(container.textContent).toContain("Lv4");
  });
});
