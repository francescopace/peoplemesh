import { el } from "../ui.js";

export function createSearchFilterBar({ tabs, activeTypeFilter = "" }) {
  const filterBar = el("div", { className: "search-filter-bar" });
  filterBar.style.display = "none";

  const typeTabs = el("div", { className: "explore-type-tabs" });
  tabs.forEach((tabDef) => {
    const tab = el("button", {
      className: `explore-type-tab${tabDef.id === activeTypeFilter ? " active" : ""}`,
      dataset: { type: tabDef.id },
    },
    el("span", { className: "material-symbols-outlined icon-16" }, tabDef.icon),
    el("span", {}, tabDef.label));
    typeTabs.appendChild(tab);
  });

  const countrySelect = el("select", { className: "form-select search-country-select" });
  countrySelect.appendChild(el("option", { value: "" }, "All Countries"));
  typeTabs.appendChild(countrySelect);
  filterBar.appendChild(typeTabs);

  function setActiveTypeFilter(nextType) {
    typeTabs.querySelectorAll(".explore-type-tab").forEach((tab) => {
      tab.classList.toggle("active", tab.dataset.type === nextType);
    });
  }

  function populateCountryOptions(results) {
    const selected = countrySelect.value;
    const countries = [...new Set(
      (results || []).map((result) => result.country).filter(Boolean)
    )].sort();
    countrySelect.innerHTML = "";
    countrySelect.appendChild(el("option", { value: "" }, "All Countries"));
    countries.forEach((country) => countrySelect.appendChild(el("option", { value: country }, country)));
    if (selected && countries.includes(selected)) {
      countrySelect.value = selected;
    }
  }

  return {
    filterBar,
    typeTabs,
    countrySelect,
    setActiveTypeFilter,
    populateCountryOptions,
  };
}
