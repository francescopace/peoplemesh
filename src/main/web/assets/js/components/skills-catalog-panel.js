import { Auth } from "../auth.js";
import { el, spinner, emptyState, toast } from "../ui.js";
import {
  cleanupUnusedSkills,
  importSkillsCsv,
  listSkills,
} from "../services/skills-service.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";

export async function renderSkillsCatalogPanel(container, options = {}) {
  const {
    clearContainer = true,
    onDataChanged = null,
    wrapInCard = true,
    actionsContainer = null,
  } = options;
  container.dataset.page = "skills";
  if (clearContainer) {
    container.innerHTML = "";
  }

  const user = Auth.getUser() || {};
  const canManage = user.entitlements?.is_admin === true;
  const pageSize = 50;
  let currentPage = 0;
  let hasMore = false;
  let loading = false;
  let currentQuery = "";
  let skillsRows = [];
  let searchDebounceId = null;

  const body = el("div", { className: wrapInCard ? "profile-card-body skills-panel-body" : "skills-panel-body" });
  if (wrapInCard) {
    const panel = el("div", { className: "profile-card skills-global-panel" });
    panel.appendChild(body);
    container.appendChild(panel);
  } else {
    container.appendChild(body);
  }

  if (canManage) {
    const actions = el("div", { className: "flex gap-3", style: "align-items:center;flex-wrap:wrap" });
    const fileInput = el("input", { type: "file", accept: ".csv", style: "display:none" });
    const uploadBtn = el("button", { className: "btn btn-primary" },
      el("span", { className: "material-symbols-outlined icon-18" }, "upload"),
      el("span", {}, "Upload")
    );
    uploadBtn.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", async (event) => {
      const file = event.target.files[0];
      if (!file) return;
      uploadBtn.disabled = true;
      try {
        const payload = await file.arrayBuffer();
        const result = await importSkillsCsv(payload);
        toast(`Imported ${result?.imported ?? 0} skills`, "success");
        await refreshSkillsList();
        if (typeof onDataChanged === "function") {
          await onDataChanged();
        }
      } catch (err) {
        toast(getUserFacingErrorMessage(err, "Could not import skills CSV."), "error");
      } finally {
        uploadBtn.disabled = false;
        fileInput.value = "";
      }
    });

    const cleanupBtn = el("button", { className: "btn btn-secondary" },
      el("span", { className: "material-symbols-outlined icon-18" }, "delete_sweep"),
      el("span", {}, "Purge unused")
    );
    cleanupBtn.addEventListener("click", async () => {
      if (!confirm("Delete all skills with usage_count = 0?")) return;
      cleanupBtn.disabled = true;
      try {
        const result = await cleanupUnusedSkills();
        toast(`Deleted ${result?.deleted ?? 0} unused skills`, "success");
        await refreshSkillsList();
        if (typeof onDataChanged === "function") {
          await onDataChanged();
        }
      } catch (err) {
        toast(getUserFacingErrorMessage(err, "Could not clean up unused skills."), "error");
      } finally {
        cleanupBtn.disabled = false;
      }
    });

    actions.appendChild(uploadBtn);
    actions.appendChild(cleanupBtn);
    actions.appendChild(fileInput);
    if (actionsContainer instanceof HTMLElement) {
      actionsContainer.replaceChildren(actions);
    } else {
      body.appendChild(actions);
    }
  }

  const searchInput = el("input", {
    className: "form-input skills-list-search",
    type: "search",
    placeholder: "Search skills...",
    "aria-label": "Search skills",
  });
  searchInput.addEventListener("input", () => {
    if (searchDebounceId !== null) {
      clearTimeout(searchDebounceId);
    }
    searchDebounceId = window.setTimeout(async () => {
      currentQuery = searchInput.value.trim();
      await refreshSkillsList();
    }, 300);
  });
  searchInput.addEventListener("keydown", async (event) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    if (searchDebounceId !== null) {
      clearTimeout(searchDebounceId);
      searchDebounceId = null;
    }
    currentQuery = searchInput.value.trim();
    await refreshSkillsList();
  });

  const listArea = el("div", { className: "skills-list-area stack-3" });
  const listFooter = el("div", { className: "skills-list-footer" });
  const searchWrap = el("div", { className: "skills-footer-search" }, searchInput);
  const loadMoreBtn = el("button", { className: "btn btn-secondary", type: "button", style: "display:none" }, "Load more");
  loadMoreBtn.addEventListener("click", async () => {
    if (loading || !hasMore) return;
    await loadPage({ append: true });
  });
  listFooter.appendChild(searchWrap);
  listFooter.appendChild(loadMoreBtn);
  body.appendChild(listArea);
  body.appendChild(listFooter);
  await refreshSkillsList();

  async function refreshSkillsList() {
    const hadRows = skillsRows.length > 0;
    currentPage = 0;
    hasMore = false;
    await loadPage({ append: false, preserveExisting: hadRows });
  }

  async function loadPage({ append, preserveExisting = false }) {
    loading = true;
    updateControlsState();
    if (!append && !preserveExisting) {
      listArea.innerHTML = "";
      listArea.appendChild(spinner());
    }
    try {
      const query = { page: currentPage, size: pageSize };
      if (currentQuery) {
        query.q = currentQuery;
      }
      const pageRows = await listSkills(query);
      const normalizedRows = Array.isArray(pageRows) ? pageRows : [];
      skillsRows = append ? skillsRows.concat(normalizedRows) : normalizedRows;
      currentPage += 1;
      hasMore = normalizedRows.length === pageSize;
      renderSkillsTable();
    } catch (err) {
      if (!append && !preserveExisting) {
        listArea.innerHTML = "";
        listArea.appendChild(emptyState("Could not load global skills."));
      }
      toast(getUserFacingErrorMessage(err, "Could not load global skills."), "error");
    } finally {
      loading = false;
      updateControlsState();
    }
  }

  function renderSkillsTable() {
    listArea.innerHTML = "";
    if (!skillsRows.length) {
      listArea.appendChild(emptyState(currentQuery ? "No skills match your search." : "No skills available yet."));
      return;
    }
    const tableWrap = el("div", { className: "skills-table-wrap" });
    const headTable = el("table", { className: "skills-table skills-table--head" });
    headTable.appendChild(buildSkillsColGroup());
    headTable.appendChild(el("thead", {},
      el("tr", {},
        el("th", {}, "Skill"),
        el("th", {}, "Aliases"),
        el("th", {}, "Usage")
      )
    ));
    const headSection = el("div", { className: "skills-table-head" }, headTable);

    const bodyScroll = el("div", { className: "skills-table-body-scroll" });
    const bodyTable = el("table", { className: "skills-table skills-table--body" });
    bodyTable.appendChild(buildSkillsColGroup());
    const tbody = el("tbody", {});
    skillsRows.forEach((skill) => {
      tbody.appendChild(el("tr", {},
        el("td", { className: "skills-cell-name" }, skill.name || "\u2014"),
        el("td", { className: "skills-cell-aliases text-secondary" }, Array.isArray(skill.aliases) ? skill.aliases.join(", ") : "\u2014"),
        el("td", { className: "skills-cell-usage" }, String(skill.usageCount ?? 0))
      ));
    });
    bodyTable.appendChild(tbody);
    bodyScroll.appendChild(bodyTable);
    tableWrap.appendChild(headSection);
    tableWrap.appendChild(bodyScroll);
    listArea.appendChild(tableWrap);
  }

  function buildSkillsColGroup() {
    return el("colgroup", {},
      el("col", { style: "width:26%" }),
      el("col", { style: "width:58%" }),
      el("col", { style: "width:16%" })
    );
  }

  function updateControlsState() {
    loadMoreBtn.disabled = loading;
    loadMoreBtn.style.display = hasMore ? "" : "none";
    loadMoreBtn.textContent = loading && hasMore ? "Loading..." : "Load more";
  }
}
