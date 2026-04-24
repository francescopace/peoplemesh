export const EXPLORE_PAGE_SIZE = 9;

export function buildExplorePageQuery({ activeType, country, offset }) {
  const query = {
    limit: EXPLORE_PAGE_SIZE,
    offset,
  };
  if (country) query.country = country;
  if (activeType) query.type = activeType;
  return query;
}

export function mergeExplorePageState({ currentOffset, pageResults }) {
  return {
    hasMore: pageResults.length === EXPLORE_PAGE_SIZE,
    currentOffset: currentOffset + pageResults.length,
  };
}

export function getExploreCompletionToastMessage({ append, elapsedMs }) {
  const seconds = (elapsedMs / 1000).toFixed(1);
  if (append) return `Loaded more My Mesh results in ${seconds}s`;
  return `My Mesh updated in ${seconds}s`;
}
