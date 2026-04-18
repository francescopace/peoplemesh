import { api } from "../api.js";
import { el, spinner, emptyState, toast } from "../ui.js";
import { NODE_TYPE_ICONS, NODE_TYPE_COLORS } from "../node-types.js";
import { contactFooter } from "../contact-actions.js";
import { termsMatch } from "../utils/term-matching.js";
import {
  adaptMatchesToSearchResponse,
  buildProfileSchemaFromParsedQuery,
  inferAutoTypeFromParsedQuery,
  toMatchesTypeFilter,
} from "../utils/search-query-mapper.js";

const RESULT_TYPE_TABS = [
  { id: "",        label: "All",         icon: "layers" },
  { id: "profile", label: "People",      icon: "person" },
  { id: "JOB",     label: "Jobs",        icon: "work" },
  { id: "COMMUNITY", label: "Communities", icon: "groups" },
  { id: "EVENT",   label: "Events",      icon: "event" },
  { id: "PROJECT", label: "Projects",    icon: "rocket_launch" },
  { id: "INTEREST_GROUP", label: "Groups", icon: "interests" },
];
const SEARCH_PAGE_SIZE = 10;

export async function renderSearch(container) {
  container.dataset.page = "search";

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

  const filterBar = el("div", { className: "search-filter-bar" });
  filterBar.style.display = "none";

  let activeTypeFilter = "";
  const typeTabs = el("div", { className: "explore-type-tabs" });
  RESULT_TYPE_TABS.forEach((t) => {
    const tab = el("button", {
      className: `explore-type-tab${t.id === activeTypeFilter ? " active" : ""}`,
      dataset: { type: t.id },
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, t.icon),
      el("span", {}, t.label)
    );
    typeTabs.appendChild(tab);
  });
  const countrySelect = el("select", { className: "form-select search-country-select" });
  countrySelect.appendChild(el("option", { value: "" }, "All Countries"));
  typeTabs.appendChild(countrySelect);
  filterBar.appendChild(typeTabs);

  root.appendChild(filterBar);

  const resultsArea = el("div", { className: "search-results" });
  root.appendChild(resultsArea);

  let lastQueryText = "";
  let lastParsedQuery = null;
  let lastDerivedProfileSchema = null;
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
    renderResults(
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
      let pageResults = [];

      if (mode === "matches") {
        const typeParam = toMatchesTypeFilter(activeTypeFilter);
        const query = new URLSearchParams();
        if (typeParam) query.set("type", typeParam);
        if (countrySelect.value) query.set("country", countrySelect.value);
        query.set("limit", String(SEARCH_PAGE_SIZE));
        query.set("offset", String(currentOffset));
        const path = `/api/v1/matches?${query.toString()}`;
        const matches = await api.post(path, lastDerivedProfileSchema);
        const adapted = adaptMatchesToSearchResponse(matches, lastParsedQuery);
        pageResults = adapted.results || [];
      } else {
        const query = new URLSearchParams();
        query.set("limit", String(SEARCH_PAGE_SIZE));
        query.set("offset", String(currentOffset));
        const data = await api.post(`/api/v1/matches/prompt?${query.toString()}`, { query: lastQueryText });
        pageResults = data.results || [];
        if (!lastParsedQuery && data.parsedQuery) {
          lastParsedQuery = data.parsedQuery;
          lastDerivedProfileSchema = buildProfileSchemaFromParsedQuery(lastParsedQuery);
          const autoType = inferAutoTypeFromParsedQuery(lastParsedQuery);
          if (autoType && RESULT_TYPE_TABS.some((t) => t.id === autoType)) {
            activeTypeFilter = autoType;
            typeTabs.querySelectorAll(".explore-type-tab").forEach((t) =>
              t.classList.toggle("active", t.dataset.type === activeTypeFilter));
          }
        }
      }

      const elapsedMs = performance.now() - t0;
      loadedResults = append ? loadedResults.concat(pageResults) : pageResults.slice();
      hasMore = pageResults.length === SEARCH_PAGE_SIZE;
      currentOffset += pageResults.length;

      if (!countrySelect.value) {
        populateCountryOptions(loadedResults);
      }
      // Keep filters available after a search even when current result set is empty,
      // so users can change tab/country and retry without retyping the query.
      filterBar.style.display = hasSearched ? "" : "none";
      renderCurrentResults();

      if (append) {
        toast(`Loaded more search results in ${(elapsedMs / 1000).toFixed(1)}s`, "info", 2200);
      } else if (mode === "matches") {
        toast(`Search filters applied in ${(elapsedMs / 1000).toFixed(1)}s`, "info", 2200);
      } else {
        toast(`Search query completed in ${(elapsedMs / 1000).toFixed(1)}s`, "info", 2200);
      }
    } catch (err) {
      resultsArea.innerHTML = "";
      if (err.status === 429) {
        resultsArea.appendChild(emptyState("Too many requests. Please wait a moment and try again."));
      } else {
        resultsArea.appendChild(emptyState("Something went wrong. Please try again."));
      }
      toast(err.message, "error");
    }
  }

  typeTabs.addEventListener("click", (e) => {
    const tab = e.target.closest(".explore-type-tab");
    if (!tab) return;
    if (tab.dataset.type === activeTypeFilter) return;
    activeTypeFilter = tab.dataset.type;
    typeTabs.querySelectorAll(".explore-type-tab").forEach((t) =>
      t.classList.toggle("active", t.dataset.type === activeTypeFilter));
    if (!hasSearched) return;
    activeBackendMode = lastDerivedProfileSchema ? "matches" : "prompt";
    resetPagedState();
    loadPagedResults(false);
  });

  countrySelect.addEventListener("change", () => {
    if (!hasSearched) return;
    activeBackendMode = lastDerivedProfileSchema ? "matches" : "prompt";
    resetPagedState();
    loadPagedResults(false);
  });

  function populateCountryOptions(results) {
    const selected = countrySelect.value;
    const countries = [...new Set(
      (results || []).map((r) => r.country).filter(Boolean)
    )].sort();
    countrySelect.innerHTML = "";
    countrySelect.appendChild(el("option", { value: "" }, "All Countries"));
    countries.forEach((c) => countrySelect.appendChild(el("option", { value: c }, c)));
    if (selected && countries.includes(selected)) {
      countrySelect.value = selected;
    }
  }

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
    lastDerivedProfileSchema = null;
    activeTypeFilter = "";
    activeBackendMode = "prompt";
    resetPagedState();
    typeTabs.querySelectorAll(".explore-type-tab").forEach((t) =>
      t.classList.toggle("active", t.dataset.type === ""));
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

function renderResults(container, results, typeFilter, countryFilter, hasMore, hasSearched, onLoadMore) {
  container.innerHTML = "";

  if (!results || results.length === 0) {
    if (hasSearched && (typeFilter || countryFilter)) {
      container.appendChild(emptyState("No results match the selected filters."));
    } else {
      container.appendChild(emptyState("No results found. Try a different query."));
    }
    return;
  }

  let filtered = results;
  if (typeFilter) {
    filtered = filtered.filter((r) =>
      typeFilter === "profile" ? r.resultType === "profile" : r.nodeType === typeFilter
    );
  }
  if (countryFilter) {
    filtered = filtered.filter((r) => r.country === countryFilter);
  }

  if (!filtered.length) {
    container.appendChild(emptyState("No results match the selected filters."));
    return;
  }

  const grid = el("div", { className: "search-results-grid" });
  filtered.forEach((r) => {
    if (r.resultType === "profile") {
      grid.appendChild(renderProfileCard(r));
    } else {
      grid.appendChild(renderNodeCard(r));
    }
  });
  container.appendChild(grid);

  if (hasMore) {
    const loadMoreBtn = el("button", {
      className: "btn btn-secondary",
      type: "button",
      onClick: onLoadMore,
    }, "Load more");
    container.appendChild(el("div", { className: "search-load-more" }, loadMoreBtn));
  }
}

function renderProfileCard(result) {
  const colors = NODE_TYPE_COLORS.PEOPLE;
  const card = el("div", { className: "discover-card" });
  card.style.borderLeft = `3px solid ${colors.border}`;

  const hdr = el("div", { className: "dc-header" });
  const avatarWrap = el("div", { className: "dc-avatar-wrap" });

  const fallbackEl = () => el("div", {
    className: "dc-avatar dc-avatar--icon",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, el("span", { className: "material-symbols-outlined" }, "person"));

  if (result.avatarUrl) {
    const img = el("img", {
      className: "dc-avatar dc-avatar--photo",
      src: result.avatarUrl,
      alt: result.displayName || "",
      referrerPolicy: "no-referrer",
    });
    img.onerror = () => img.replaceWith(fallbackEl());
    avatarWrap.appendChild(img);
  } else {
    avatarWrap.appendChild(fallbackEl());
  }

  const info = el("div", { className: "dc-info" });
  const nameLink = el("a", {
    href: `#/people/${result.id}`,
    className: "dc-name-link",
    style: "text-decoration:none;color:inherit",
  }, result.displayName || "Anonymous");
  info.appendChild(el("h3", { className: "dc-name" }, nameLink));

  const subtitle = el("div", { className: "dc-subtitle" });
  subtitle.appendChild(el("span", {
    className: "dc-type-badge",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, "PROFILE"));
  const locationParts = [result.city, result.country].filter(Boolean);
  if (locationParts.length) {
    subtitle.appendChild(el("span", { className: "dc-sep" }, "\u00B7"));
    subtitle.appendChild(el("span", { style: "font-size:0.72rem" }, locationParts.join(", ")));
  }
  info.appendChild(subtitle);
  avatarWrap.appendChild(info);
  hdr.appendChild(avatarWrap);

  hdr.appendChild(el("div", { className: "dc-score", style: `color:${colors.color}` },
    `${Math.round(result.score * 100)}%`));
  card.appendChild(hdr);

  if (result.roles?.length) {
    card.appendChild(el("p", { className: "dc-meta" }, result.roles.join(" \u00b7 ")));
  }

  const allSkills = [...new Set([
    ...(result.skillsTechnical || []),
    ...(result.toolsAndTech || []),
  ].map((s) => s.trim()))];
  if (allSkills.length) {
    const matched = [
      ...(result.breakdown?.matchedMustHaveSkills || []),
      ...(result.breakdown?.matchedNiceToHaveSkills || []),
    ];
    const skillLevels = result.skill_levels || {};
    const matchStyle = `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; box-shadow:0 0 0 1px ${colors.border}`;
    const tagsArea = el("div", { className: "dc-tags-area" });
    const row = el("div", { className: "dc-tags" });
    allSkills.slice(0, 10).forEach((s) => {
      const isMatch = matched.some((m) => termsMatch(m, s));
      const tag = el("span", { className: "dc-tag", style: isMatch ? matchStyle : undefined }, s);
      const lvl = findSkillLevel(skillLevels, s);
      if (lvl > 0) {
        tag.appendChild(el("span", { className: "skill-level-badge" }, `Lv${lvl}`));
      }
      row.appendChild(tag);
    });
    tagsArea.appendChild(row);
    card.appendChild(tagsArea);
  }

  const actions = contactFooter(
    result.slackHandle,
    result.email,
    result.telegramHandle,
    result.mobilePhone
  );
  if (actions) card.appendChild(actions);

  return card;
}

function renderNodeCard(result) {
  const nType = result.nodeType || "COMMUNITY";
  const iconName = NODE_TYPE_ICONS[nType] || "layers";
  const colors = NODE_TYPE_COLORS[nType] || NODE_TYPE_COLORS.JOB;

  const card = el("div", { className: "discover-card" });
  card.style.borderLeft = `3px solid ${colors.border}`;

  const hdr = el("div", { className: "dc-header" });
  const avatarWrap = el("div", { className: "dc-avatar-wrap" });
  avatarWrap.appendChild(el("div", {
    className: "dc-avatar dc-avatar--icon",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, el("span", { className: "material-symbols-outlined" }, iconName)));

  const info = el("div", { className: "dc-info" });
  const nodeTitle = result.title || "Untitled";
  if (nType === "JOB") {
    const jobLink = el("a", {
      href: `#/jobs/${result.id}`,
      className: "dc-name-link",
      style: "text-decoration:none;color:inherit",
    }, nodeTitle);
    info.appendChild(el("h3", { className: "dc-name" }, jobLink));
  } else {
    info.appendChild(el("h3", { className: "dc-name" }, nodeTitle));
  }

  const subtitle = el("div", { className: "dc-subtitle" });
  subtitle.appendChild(el("span", {
    className: "dc-type-badge",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, nType.replace("_", " ")));
  if (result.country) {
    subtitle.appendChild(el("span", { className: "dc-sep" }, "\u00B7"));
    subtitle.appendChild(el("span", { style: "font-size:0.72rem" }, result.country));
  }
  info.appendChild(subtitle);
  avatarWrap.appendChild(info);
  hdr.appendChild(avatarWrap);

  hdr.appendChild(el("div", { className: "dc-score", style: `color:${colors.color}` },
    `${Math.round(result.score * 100)}%`));
  card.appendChild(hdr);

  if (result.description) {
    const desc = result.description.length > 120
      ? result.description.substring(0, 120) + "\u2026" : result.description;
    card.appendChild(el("p", { className: "dc-desc" }, desc));
  }

  if (result.tags?.length) {
    const matchedTerms = result.breakdown?.matchedMustHaveSkills || [];
    const matchStyle = `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; box-shadow:0 0 0 1px ${colors.border}`;
    const tagsArea = el("div", { className: "dc-tags-area" });
    const row = el("div", { className: "dc-tags" });
    result.tags.slice(0, 8).forEach((t) => {
      const isMatch = matchedTerms.some((m) => termsMatch(m, t));
      row.appendChild(el("span", { className: "dc-tag", style: isMatch ? matchStyle : undefined }, t));
    });
    tagsArea.appendChild(row);
    card.appendChild(tagsArea);
  }

  return card;
}

function findSkillLevel(skillLevels, skillName) {
  if (!skillLevels) return 0;
  const direct = skillLevels[skillName];
  if (direct != null) return direct;
  const sLow = skillName.toLowerCase();
  for (const [k, v] of Object.entries(skillLevels)) {
    if (k.toLowerCase() === sLow) return v;
  }
  return 0;
}

