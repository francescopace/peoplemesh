import { describe, expect, it } from "vitest";
import { createSearchFilterBar } from "../../assets/js/components/search-filters.js";

const TABS = [
  { id: "", label: "All", icon: "layers" },
  { id: "PEOPLE", label: "People", icon: "person" },
  { id: "JOB", label: "Jobs", icon: "work" },
];

describe("search-filters component", () => {
  it("renders tabs and active filter state", () => {
    const { typeTabs, setActiveTypeFilter } = createSearchFilterBar({
      tabs: TABS,
      activeTypeFilter: "",
    });

    expect(typeTabs.querySelectorAll(".explore-type-tab")).toHaveLength(3);
    expect(typeTabs.querySelector('.explore-type-tab[data-type=""]').classList.contains("active")).toBe(true);

    setActiveTypeFilter("PEOPLE");
    expect(typeTabs.querySelector('.explore-type-tab[data-type="PEOPLE"]').classList.contains("active")).toBe(true);
    expect(typeTabs.querySelector('.explore-type-tab[data-type=""]').classList.contains("active")).toBe(false);
  });

  it("populates country options and keeps selected value when still available", () => {
    const { countrySelect, populateCountryOptions } = createSearchFilterBar({
      tabs: TABS,
      activeTypeFilter: "",
    });

    populateCountryOptions([{ country: "US" }, { country: "IT" }, { country: "US" }]);
    countrySelect.value = "US";
    populateCountryOptions([{ country: "DE" }, { country: "US" }]);

    const options = [...countrySelect.querySelectorAll("option")].map((opt) => opt.value);
    expect(options).toEqual(["", "DE", "US"]);
    expect(countrySelect.value).toBe("US");
  });
});
