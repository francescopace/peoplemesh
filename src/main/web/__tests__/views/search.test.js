import { describe, it, expect, vi, beforeEach } from "vitest";

let renderSearch;
let apiMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("search view skill highlighting", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/search";

    apiMock = {
      post: vi.fn(),
    };

    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    vi.doMock("../../assets/js/contact-actions.js", () => ({
      contactFooter: () => null,
    }));

    const mod = await import("../../assets/js/views/search.js");
    renderSearch = mod.renderSearch;
  });

  it("does not highlight JavaScript when only Java is matched", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-1",
          resultType: "profile",
          displayName: "Alice",
          score: 0.88,
          skillsTechnical: ["JavaScript", "Java"],
          toolsAndTech: [],
          breakdown: {
            matchedMustHaveSkills: ["Java"],
            matchedNiceToHaveSkills: [],
          },
          skill_levels: {},
        },
      ],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "java developer";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const tags = [...container.querySelectorAll(".dc-tag")];
    const jsTag = tags.find((t) => t.textContent.trim() === "JavaScript");
    const javaTag = tags.find((t) => t.textContent.trim() === "Java");

    expect(jsTag).toBeTruthy();
    expect(javaTag).toBeTruthy();
    expect(jsTag.getAttribute("style")).not.toContain("box-shadow");
    expect(javaTag.getAttribute("style")).toContain("box-shadow");
  });

  it("keeps highlighting token-subset matches like React and React Native", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-2",
          resultType: "profile",
          displayName: "Bob",
          score: 0.79,
          skillsTechnical: ["React Native"],
          toolsAndTech: [],
          breakdown: {
            matchedMustHaveSkills: ["React"],
            matchedNiceToHaveSkills: [],
          },
          skill_levels: {},
        },
      ],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "react mobile";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const reactNativeTag = [...container.querySelectorAll(".dc-tag")]
      .find((t) => t.textContent.trim() === "React Native");

    expect(reactNativeTag).toBeTruthy();
    expect(reactNativeTag.getAttribute("style")).toContain("box-shadow");
  });

  it("switching tab after prompt uses /matches endpoint", async () => {
    apiMock.post
      .mockResolvedValueOnce({
        parsedQuery: {
          must_have: { roles: [], skills: ["community"], languages: [], location: [], industries: [] },
          nice_to_have: { skills: [], industries: [], experience: [] },
          seniority: "unknown",
          negative_filters: { seniority: null, skills: [], location: [] },
          keywords: ["community"],
          embedding_text: "community in italy",
        },
        results: [
          {
            id: "n-1",
            resultType: "node",
            nodeType: "COMMUNITY",
            title: "Java Guild",
            score: 0.9,
            tags: ["java"],
            breakdown: { matchedMustHaveSkills: ["community"], matchedNiceToHaveSkills: [] },
          },
        ],
      })
      .mockResolvedValueOnce([
        {
          id: "n-2",
          nodeType: "PEOPLE",
          title: "Alice",
          score: 0.85,
          person: {
            roles: ["Developer"],
            skillsTechnical: ["community"],
            toolsAndTech: [],
          },
          tags: [],
          breakdown: { commonItems: ["platform"] },
        },
      ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "community in italy";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const peopleTab = [...container.querySelectorAll(".explore-type-tab")]
      .find((btn) => btn.dataset.type === "profile");
    expect(peopleTab).toBeTruthy();
    peopleTab.dispatchEvent(new Event("click", { bubbles: true }));
    await flushPromises();

    expect(apiMock.post.mock.calls[0][0]).toContain("/api/v1/matches/prompt?");
    expect(apiMock.post.mock.calls[0][0]).toContain("limit=10");
    expect(apiMock.post.mock.calls[0][0]).toContain("offset=0");
    expect(apiMock.post.mock.calls[0][1]).toEqual({ query: "community in italy" });
    expect(apiMock.post.mock.calls[1][0]).toContain("/api/v1/matches?type=PEOPLE");
    expect(apiMock.post.mock.calls[1][0]).toContain("limit=10");
    expect(apiMock.post.mock.calls[1][0]).toContain("offset=0");
    expect(apiMock.post.mock.calls[1][1]).toEqual({
      must_have: { roles: [], skills: ["community"], languages: [], location: [], industries: [] },
      nice_to_have: { skills: [], industries: [], experience: [] },
      seniority: "unknown",
      negative_filters: { seniority: null, skills: [], location: [] },
      keywords: ["community"],
      embedding_text: "community in italy",
    });

    const highlightedTag = [...container.querySelectorAll(".dc-tag")]
      .find((t) => t.textContent.trim() === "community");
    expect(highlightedTag).toBeTruthy();
    expect(highlightedTag.getAttribute("style")).toContain("box-shadow");
  });

  it("keeps All tab active when parsedQuery result_scope is all", async () => {
    apiMock.post.mockResolvedValue({
      parsedQuery: {
        must_have: { roles: ["architect"], skills: ["Java", "Kubernetes"], languages: [], location: [], industries: [] },
        nice_to_have: { skills: [], industries: [], experience: [] },
        seniority: "unknown",
        negative_filters: { seniority: null, skills: [], location: [] },
        keywords: ["all results"],
        embedding_text: "all results with Java and Kubernetes",
        result_scope: "all",
      },
      results: [
        {
          id: "p-1",
          resultType: "profile",
          displayName: "Alice",
          score: 0.8,
          skillsTechnical: ["Java", "Kubernetes"],
          toolsAndTech: [],
          breakdown: { matchedMustHaveSkills: ["Java"], matchedNiceToHaveSkills: [] },
          skill_levels: {},
        },
      ],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "tutti i risultati con Java e Kubernetes";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const allTab = [...container.querySelectorAll(".explore-type-tab")]
      .find((btn) => btn.dataset.type === "");
    const peopleTab = [...container.querySelectorAll(".explore-type-tab")]
      .find((btn) => btn.dataset.type === "profile");
    expect(allTab).toBeTruthy();
    expect(peopleTab).toBeTruthy();
    expect(allTab.classList.contains("active")).toBe(true);
    expect(peopleTab.classList.contains("active")).toBe(false);
  });

  it("load more fetches next prompt page from backend", async () => {
    const makeProfile = (id) => ({
      id,
      resultType: "profile",
      displayName: `User ${id}`,
      score: 0.7,
      skillsTechnical: ["Java"],
      toolsAndTech: [],
      breakdown: {
        matchedMustHaveSkills: [],
        matchedNiceToHaveSkills: [],
      },
      skill_levels: {},
    });
    apiMock.post
      .mockResolvedValueOnce({
        results: Array.from({ length: 10 }, (_, i) => makeProfile(`u-${i}`)),
      })
      .mockResolvedValueOnce({
        results: [makeProfile("u-10")],
      });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "java developer";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const loadMoreBtn = container.querySelector(".search-load-more .btn");
    expect(loadMoreBtn).toBeTruthy();
    loadMoreBtn.dispatchEvent(new Event("click", { bubbles: true }));
    await flushPromises();

    expect(apiMock.post.mock.calls[1][0]).toContain("/api/v1/matches/prompt?");
    expect(apiMock.post.mock.calls[1][0]).toContain("offset=10");
    expect(container.querySelectorAll(".discover-card").length).toBe(11);
  });

  it("shows geography tag when backend provides positive geography reason", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-geo",
          resultType: "profile",
          displayName: "Geo User",
          score: 0.81,
          city: "Milan",
          country: "IT",
          skillsTechnical: ["Java"],
          toolsAndTech: [],
          breakdown: {
            matchedMustHaveSkills: ["Java"],
            matchedNiceToHaveSkills: [],
            geographyReason: "same_country",
            geographyScore: 1,
          },
          skill_levels: {},
        },
      ],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "java";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const geoTag = [...container.querySelectorAll(".dc-tag")]
      .find((t) => t.textContent.trim() === "same country");
    expect(geoTag).toBeTruthy();
  });

  it("keeps filter bar visible when a search returns no results", async () => {
    apiMock.post.mockResolvedValue({
      parsedQuery: {
        must_have: { roles: [], skills: ["golang"], languages: [], location: [], industries: [] },
        nice_to_have: { skills: [], industries: [], experience: [] },
        seniority: "unknown",
        negative_filters: { seniority: null, skills: [], location: [] },
        keywords: ["golang"],
        embedding_text: "golang developers",
      },
      results: [],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "golang developers";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const filterBar = container.querySelector(".search-filter-bar");
    expect(filterBar).toBeTruthy();
    expect(filterBar.style.display).not.toBe("none");
  });
});
