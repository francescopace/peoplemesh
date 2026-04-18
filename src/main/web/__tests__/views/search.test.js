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
});
