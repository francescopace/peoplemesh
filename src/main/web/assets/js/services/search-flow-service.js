import { toMatchesTypeFilter } from "../utils/search-query-mapper.js";

export const SEARCH_PAGE_SIZE = 10;

export function buildSearchPageRequest({
  mode,
  queryText,
  parsedQuery,
  activeTypeFilter,
  country,
  offset,
}) {
  return {
    mode,
    queryText,
    parsedQuery,
    type: toMatchesTypeFilter(activeTypeFilter),
    country,
    limit: SEARCH_PAGE_SIZE,
    offset,
  };
}

export function mergeSearchPageState({ append, loadedResults, pageResults, currentOffset }) {
  const nextResults = append ? loadedResults.concat(pageResults) : pageResults.slice();
  return {
    loadedResults: nextResults,
    hasMore: pageResults.length === SEARCH_PAGE_SIZE,
    currentOffset: currentOffset + pageResults.length,
  };
}

export function getSearchCompletionToastMessage({ append, mode, elapsedMs }) {
  const seconds = (elapsedMs / 1000).toFixed(1);
  if (append) return `Loaded more search results in ${seconds}s`;
  if (mode === "matches") return `Search filters applied in ${seconds}s`;
  return `Search query completed in ${seconds}s`;
}
