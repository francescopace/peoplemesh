import { el, spinner, emptyState, toast } from "../ui.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import {
  fetchSearchResultsPage,
} from "../services/matches-service.js";
import {
  buildSearchPageRequest,
  getSearchCompletionToastMessage,
  mergeSearchPageState,
} from "../services/search-flow-service.js";
import {
  inferAutoTypeFromParsedQuery,
} from "../utils/search-query-mapper.js";
import { createSearchFilterBar } from "../components/search-filters.js";
import { renderSearchResults } from "../components/search-results.js";

const RESULT_TYPE_TABS = [
  { id: "",        label: "All",         icon: "layers" },
  { id: "PEOPLE",  label: "People",      icon: "person" },
  { id: "JOB",     label: "Jobs",        icon: "work" },
  { id: "COMMUNITY", label: "Communities", icon: "groups" },
  { id: "EVENT",   label: "Events",      icon: "event" },
  { id: "PROJECT", label: "Projects",    icon: "rocket_launch" },
  { id: "INTEREST_GROUP", label: "Groups", icon: "interests" },
];
export async function renderSearch(container) {
  container.dataset.page = "search";
  container.innerHTML = "";

  const root = el("div", { className: "search-page" });
  const hero = el("div", { className: "search-hero" });
  const form = el("form", { className: "search-input-wrap" });
  const input = el("input", {
    type: "text",
    className: "search-input",
    placeholder: "Search\u2026 e.g. 'OpenShift consultant' or 'AI community in Italy'",
    autocomplete: "off",
    maxlength: "500",
  });
  const btn = el("button", { type: "submit", className: "search-btn" },
    el("span", { className: "material-symbols-outlined" }, "search"));
  form.appendChild(input);
  form.appendChild(btn);
  hero.appendChild(form);

  root.appendChild(hero);

  let activeTypeFilter = "";
  const {
    filterBar,
    typeTabs,
    countrySelect,
    setActiveTypeFilter,
    populateCountryOptions,
  } = createSearchFilterBar({
    tabs: RESULT_TYPE_TABS,
    activeTypeFilter,
  });

  root.appendChild(filterBar);

  const resultsArea = el("div", { className: "search-results" });
  root.appendChild(resultsArea);

  let lastQueryText = "";
  let lastParsedQuery = null;
  let loadedResults = [];
  let currentOffset = 0;
  let hasMore = false;
  let activeBackendMode = "prompt";
  let hasSearched = false;

  function resetPagedState() {
    loadedResults = [];
    currentOffset = 0;
    hasMore = false;
  }

  function renderCurrentResults() {
    renderSearchResults(
      resultsArea,
      loadedResults,
      activeTypeFilter,
      countrySelect.value,
      hasMore,
      hasSearched,
      () => loadPagedResults(true)
    );
  }

  async function loadPagedResults(append) {
    if (append && !hasMore) return;

    const mode = activeBackendMode;
    if (append) {
      renderCurrentResults();
      const loadMoreBtn = resultsArea.querySelector(".search-load-more .btn");
      if (loadMoreBtn) {
        loadMoreBtn.disabled = true;
        loadMoreBtn.textContent = "Loading...";
      }
    } else {
      resultsArea.innerHTML = "";
      resultsArea.appendChild(spinner());
    }

    try {
      const t0 = performance.now();
      const request = buildSearchPageRequest({
        mode,
        queryText: lastQueryText,
        parsedQuery: lastParsedQuery,
        activeTypeFilter,
        country: countrySelect.value,
        offset: currentOffset,
      });
      const page = await fetchSearchResultsPage(request);
      const pageResults = page.results || [];
      if (!lastParsedQuery && page.parsedQuery) {
        lastParsedQuery = page.parsedQuery;
        const autoType = inferAutoTypeFromParsedQuery(lastParsedQuery);
        if (autoType && RESULT_TYPE_TABS.some((t) => t.id === autoType)) {
          activeTypeFilter = autoType;
          setActiveTypeFilter(activeTypeFilter);
        }
      }

      const elapsedMs = performance.now() - t0;
      const mergedState = mergeSearchPageState({
        append,
        loadedResults,
        pageResults,
        currentOffset,
      });
      loadedResults = mergedState.loadedResults;
      hasMore = mergedState.hasMore;
      currentOffset = mergedState.currentOffset;

      if (!countrySelect.value) {
        populateCountryOptions(loadedResults);
      }
      // Keep filters available after a search even when current result set is empty,
      // so users can change tab/country and retry without retyping the query.
      filterBar.style.display = hasSearched ? "" : "none";
      renderCurrentResults();

      toast(getSearchCompletionToastMessage({ append, mode, elapsedMs }), "info", 2200);
    } catch (err) {
      resultsArea.innerHTML = "";
      if (err.status === 429) {
        resultsArea.appendChild(emptyState("Too many requests. Please wait a moment and try again."));
      } else {
        resultsArea.appendChild(emptyState("Something went wrong. Please try again."));
      }
      toast(getUserFacingErrorMessage(err), "error");
    }
  }

  typeTabs.addEventListener("click", (e) => {
    const tab = e.target.closest(".explore-type-tab");
    if (!tab) return;
    if (tab.dataset.type === activeTypeFilter) return;
    activeTypeFilter = tab.dataset.type;
    setActiveTypeFilter(activeTypeFilter);
    if (!hasSearched) return;
    activeBackendMode = lastParsedQuery ? "matches" : "prompt";
    resetPagedState();
    loadPagedResults(false);
  });

  countrySelect.addEventListener("change", () => {
    if (!hasSearched) return;
    activeBackendMode = lastParsedQuery ? "matches" : "prompt";
    resetPagedState();
    loadPagedResults(false);
  });

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const query = input.value.trim();
    if (!query) return;

    filterBar.style.display = "none";
    resultsArea.innerHTML = "";
    resultsArea.appendChild(spinner());
    lastQueryText = query;
    hasSearched = true;
    lastParsedQuery = null;
    activeTypeFilter = "";
    activeBackendMode = "prompt";
    resetPagedState();
    setActiveTypeFilter(activeTypeFilter);
    countrySelect.value = "";
    await loadPagedResults(false);
  });

  container.appendChild(root);

  const hashQuery = window.location.hash.split("?")[1];
  const urlParams = new URLSearchParams(hashQuery || "");
  const initialQuery = urlParams.get("q");
  if (initialQuery) {
    input.value = initialQuery;
    form.requestSubmit();
  } else {
    input.focus();
  }
}
