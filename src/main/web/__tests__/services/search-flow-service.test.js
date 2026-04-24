import { describe, expect, it } from "vitest";
import {
  buildSearchPageRequest,
  getSearchCompletionToastMessage,
  mergeSearchPageState,
  SEARCH_PAGE_SIZE,
} from "../../assets/js/services/search-flow-service.js";

describe("search-flow-service", () => {
  it("buildSearchPageRequest preserves selected node type filter", () => {
    const request = buildSearchPageRequest({
      mode: "matches",
      queryText: "java",
      parsedQuery: { must_have: {} },
      activeTypeFilter: "PEOPLE",
      country: "IT",
      offset: 20,
    });

    expect(request).toEqual({
      mode: "matches",
      queryText: "java",
      parsedQuery: { must_have: {} },
      type: "PEOPLE",
      country: "IT",
      limit: SEARCH_PAGE_SIZE,
      offset: 20,
    });
  });

  it("mergeSearchPageState appends and updates pagination", () => {
    const state = mergeSearchPageState({
      append: true,
      loadedResults: [{ id: "a" }],
      pageResults: [{ id: "b" }],
      currentOffset: 10,
    });
    expect(state.loadedResults).toEqual([{ id: "a" }, { id: "b" }]);
    expect(state.currentOffset).toBe(11);
    expect(state.hasMore).toBe(false);
  });

  it("builds completion toasts by mode", () => {
    expect(getSearchCompletionToastMessage({ append: true, mode: "prompt", elapsedMs: 1000 }))
      .toBe("Loaded more search results in 1.0s");
    expect(getSearchCompletionToastMessage({ append: false, mode: "matches", elapsedMs: 1000 }))
      .toBe("Search filters applied in 1.0s");
  });
});
