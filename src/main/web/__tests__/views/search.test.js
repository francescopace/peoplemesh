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

  it("hides non-requested score rows in tooltip", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-na",
          resultType: "profile",
          displayName: "Tooltip User",
          score: 0.88,
          skillsTechnical: ["Java"],
          toolsAndTech: [],
          breakdown: {
            embeddingScore: 0.81,
            mustHaveSkillCoverage: 0,
            niceToHaveBonus: 0.1,
            languageScore: 0,
            industryScore: 0,
            geographyScore: 0,
            reasonCodes: ["SENIORITY_MISMATCH"],
            mustHaveRequested: false,
            niceToHaveRequested: true,
            languageRequested: false,
            industryRequested: false,
            geographyRequested: true,
            matchedMustHaveSkills: [],
            matchedNiceToHaveSkills: ["Java"],
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

    const tooltip = container.querySelector(".dc-match-tooltip");
    expect(tooltip).toBeTruthy();
    expect(tooltip.textContent).toContain("Score points");
    expect(tooltip.textContent).toContain("Semantic similarity:");
    expect(tooltip.textContent).toContain("Nice-to-have skills:");
    expect(tooltip.textContent).not.toContain("Must-have skills:");
    expect(tooltip.textContent).not.toContain("Language:");
    expect(tooltip.textContent).not.toContain("Industry:");
    expect(tooltip.textContent).toContain("Total points:");
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

  it("shows matched profile skills before non-matched skills", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-ordered",
          resultType: "profile",
          displayName: "Order Test",
          score: 0.86,
          skillsTechnical: ["Terraform", "React", "Java", "Docker"],
          toolsAndTech: [],
          breakdown: {
            matchedMustHaveSkills: ["Java", "Docker"],
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
    input.value = "java docker";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const tags = [...container.querySelectorAll(".dc-tag")].map((t) => t.textContent.trim());
    expect(tags.slice(0, 2)).toEqual(["Java", "Docker"]);
  });

  it("shows matched node tags before non-matched tags", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "n-ordered",
          resultType: "node",
          nodeType: "PROJECT",
          title: "Order Node",
          score: 0.77,
          tags: ["terraform", "react", "java", "docker"],
          breakdown: {
            matchedMustHaveSkills: ["java", "docker"],
            matchedNiceToHaveSkills: [],
          },
        },
      ],
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);

    const input = container.querySelector(".search-input");
    const form = container.querySelector("form.search-input-wrap");
    input.value = "java docker";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    const tags = [...container.querySelectorAll(".dc-tag")].map((t) => t.textContent.trim());
    expect(tags.slice(0, 2)).toEqual(["java", "docker"]);
  });

  it("shows +N more tags when profile skills exceed visible limit", async () => {
    apiMock.post.mockResolvedValue({
      results: [
        {
          id: "u-more",
          resultType: "profile",
          displayName: "More Tags",
          score: 0.84,
          skillsTechnical: ["s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "s12", "s13", "s14", "s15", "s16", "s17"],
          toolsAndTech: [],
          breakdown: {
            matchedMustHaveSkills: ["s1"],
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
    input.value = "skills";
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await flushPromises();

    expect(container.textContent).toContain("+3 more tags");
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
      .find((btn) => btn.dataset.type === "PEOPLE");
    expect(peopleTab).toBeTruthy();
    peopleTab.dispatchEvent(new Event("click", { bubbles: true }));
    await flushPromises();

    expect(apiMock.post.mock.calls[0][0]).toContain("/api/v1/matches/prompt?");
    expect(apiMock.post.mock.calls[0][0]).toContain("limit=10");
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
      .find((btn) => btn.dataset.type === "PEOPLE");
    expect(allTab).toBeTruthy();
    expect(peopleTab).toBeTruthy();
    expect(allTab.classList.contains("active")).toBe(true);
    expect(peopleTab.classList.contains("active")).toBe(false);
  });

  it("load more after prompt parse uses /matches endpoint", async () => {
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
    const parsedQuery = {
      must_have: { roles: [], skills: ["java"], languages: [], location: [], industries: [] },
      nice_to_have: { skills: [], industries: [], experience: [] },
      seniority: "unknown",
      negative_filters: { seniority: null, skills: [], location: [] },
      keywords: ["java"],
      embedding_text: "java developer",
    };
    apiMock.post
      .mockResolvedValueOnce({
        parsedQuery,
        results: Array.from({ length: 10 }, (_, i) => makeProfile(`u-${i}`)),
      })
      .mockResolvedValueOnce([
        {
          id: "u-10",
          nodeType: "PEOPLE",
          title: "User u-10",
          score: 0.72,
          person: {
            roles: ["Developer"],
            skillsTechnical: ["Java"],
            toolsAndTech: [],
          },
          tags: [],
          breakdown: { commonItems: ["java"] },
        },
      ]);

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

    expect(apiMock.post.mock.calls[0][0]).toContain("/api/v1/matches/prompt?");
    expect(apiMock.post.mock.calls[0][0]).toContain("limit=10");
    expect(apiMock.post.mock.calls[0][1]).toEqual({ query: "java developer" });
    expect(apiMock.post.mock.calls[1][0]).toContain("/api/v1/matches?limit=10&offset=10");
    expect(apiMock.post.mock.calls[1][1]).toEqual(parsedQuery);
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

  it("does not duplicate search input when view is rendered twice", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderSearch(container);
    await renderSearch(container);

    expect(container.querySelectorAll("form.search-input-wrap").length).toBe(1);
    expect(container.querySelectorAll("input.search-input").length).toBe(1);
  });
});
