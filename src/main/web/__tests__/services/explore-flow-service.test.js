import { describe, expect, it } from "vitest";
import {
  buildExplorePageQuery,
  EXPLORE_PAGE_SIZE,
  getExploreCompletionToastMessage,
  mergeExplorePageState,
} from "../../assets/js/services/explore-flow-service.js";

describe("explore-flow-service", () => {
  it("buildExplorePageQuery includes selected filters", () => {
    expect(buildExplorePageQuery({ activeType: "PEOPLE", country: "IT", offset: 9 })).toEqual({
      type: "PEOPLE",
      country: "IT",
      limit: EXPLORE_PAGE_SIZE,
      offset: 9,
    });
  });

  it("mergeExplorePageState computes hasMore and offset", () => {
    const pageResults = Array.from({ length: EXPLORE_PAGE_SIZE }, (_, i) => ({ id: String(i) }));
    const state = mergeExplorePageState({
      currentOffset: 0,
      pageResults,
    });
    expect(state.hasMore).toBe(true);
    expect(state.currentOffset).toBe(EXPLORE_PAGE_SIZE);
  });

  it("getExploreCompletionToastMessage formats messages", () => {
    expect(getExploreCompletionToastMessage({ append: true, elapsedMs: 1200 }))
      .toBe("Loaded more My Mesh results in 1.2s");
    expect(getExploreCompletionToastMessage({ append: false, elapsedMs: 900 }))
      .toBe("My Mesh updated in 0.9s");
  });
});
