import { describe, expect, it, vi } from "vitest";

vi.mock("../../assets/js/contact-actions.js", () => ({
  contactFooter: () => null,
}));

import { renderSearchResults } from "../../assets/js/components/search-results.js";

function buildProfileResult(overrides = {}) {
  return {
    id: "p-1",
    resultType: "profile",
    displayName: "Alice",
    score: 0.82,
    city: "Rome",
    country: "IT",
    skillsTechnical: ["Java"],
    toolsAndTech: [],
    breakdown: { matchedMustHaveSkills: [], matchedNiceToHaveSkills: [] },
    skill_levels: {},
    ...overrides,
  };
}

function buildNodeResult(overrides = {}) {
  return {
    id: "n-1",
    resultType: "node",
    nodeType: "PROJECT",
    title: "Project One",
    score: 0.74,
    country: "US",
    tags: [],
    breakdown: { matchedMustHaveSkills: [], matchedNiceToHaveSkills: [] },
    ...overrides,
  };
}

describe("search-results component", () => {
  it("shows empty state when there are no results", () => {
    const container = document.createElement("div");
    renderSearchResults(container, [], "", "", false, false, () => {});
    expect(container.textContent).toContain("No results found. Try a different query.");
  });

  it("applies type and country filters before rendering cards", () => {
    const container = document.createElement("div");
    const results = [buildProfileResult(), buildNodeResult()];

    renderSearchResults(container, results, "PEOPLE", "IT", false, true, () => {});

    const cards = container.querySelectorAll(".discover-card");
    expect(cards).toHaveLength(1);
    expect(container.textContent).toContain("Alice");
    expect(container.textContent).not.toContain("Project One");
  });

  it("renders load-more button and invokes callback", () => {
    const container = document.createElement("div");
    const onLoadMore = vi.fn();
    renderSearchResults(container, [buildProfileResult()], "", "", true, true, onLoadMore);

    const loadMoreBtn = container.querySelector(".search-load-more .btn");
    expect(loadMoreBtn).not.toBeNull();
    loadMoreBtn.click();
    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });
});
