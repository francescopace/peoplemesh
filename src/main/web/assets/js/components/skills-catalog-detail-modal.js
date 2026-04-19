import { el, emptyState, modal, spinner, table, toast, toastForPromise } from "../ui.js";
import {
  deleteSkillCatalog,
  getSkillCatalog,
  importSkillCatalogCsv,
  listSkillCategories,
  listSkillDefinitions,
  updateSkillCatalog,
} from "../services/skills-service.js";

const SKILLS_PAGE_SIZE = 50;
const SKILL_DEFINITION_COLUMNS = [
  { key: "name", label: "Skill" },
  { key: "category", label: "Category" },
  { key: "aliases", label: "Aliases", render: (v) => (v || []).join(", ") || "\u2014" },
];

export async function openSkillsCatalogDetailModal(cat, canManage, onDeleted, onUpdated, onDataChanged) {
  const content = el("div", { className: "stack-4" });
  let modalHandle = null;
  let closed = false;

  const state = {
    currentCategory: "",
    catSelect: null,
    skillsArea: null,
  };

  const headerActions = canManage
    ? createManageHeaderActions(cat, state, () => closed, onDeleted, onUpdated, onDataChanged, () => modalHandle)
    : [];

  if (cat.description) {
    content.appendChild(el("p", { className: "text-secondary m-0" }, cat.description));
  }
  content.appendChild(spinner());

  modalHandle = modal(`Catalog: ${cat.name}`, content, {
    dialogClassName: "catalog-detail-dialog",
    headerActions,
    onClose: () => {
      closed = true;
    },
  });

  let categories;
  try {
    categories = await listSkillCategories(cat.id);
  } catch {
    if (closed) return;
    content.querySelector(".spinner")?.remove();
    content.appendChild(emptyState("Could not load catalog details."));
    return;
  }

  if (closed) return;
  content.querySelector(".spinner")?.remove();

  if (!categories?.length) {
    content.appendChild(emptyState("No skills in this catalog yet."));
    return;
  }

  const filterRow = el("div", { className: "flex gap-3 skills-filter-row" });
  state.catSelect = el("select", { className: "form-select skills-filter-select" });
  repopulateCategorySelect(state.catSelect, categories, "");
  filterRow.appendChild(state.catSelect);
  content.appendChild(filterRow);

  state.skillsArea = el("div", {});
  content.appendChild(state.skillsArea);

  const loadSkills = async (category, page = 0) => {
    if (closed) return;
    state.currentCategory = category || "";
    if (page === 0) {
      state.skillsArea.innerHTML = "";
      state.skillsArea.appendChild(spinner());
    } else {
      state.skillsArea.querySelector(".load-more-btn")?.remove();
    }

    try {
      const query = { page, size: SKILLS_PAGE_SIZE };
      if (category) query.category = String(category).trim().slice(0, 100);
      const skills = await listSkillDefinitions(cat.id, query);
      if (closed) return;
      state.skillsArea.querySelector(".spinner")?.remove();

      if (!skills?.length) {
        if (page === 0) state.skillsArea.appendChild(emptyState("No skills found for this filter."));
        return;
      }

      upsertSkillsTable(state.skillsArea, skills, page > 0);
      if (skills.length >= SKILLS_PAGE_SIZE) {
        const moreBtn = el("button", {
          className: "btn btn-secondary btn-sm load-more-btn",
          onClick: () => loadSkills(category, page + 1),
        }, "Load More");
        state.skillsArea.appendChild(moreBtn);
      }
    } catch (err) {
      if (closed) return;
      state.skillsArea.querySelector(".spinner")?.remove();
      state.skillsArea.appendChild(emptyState("Error loading skills."));
      toast(err.message, "error");
    }
  };

  state.catSelect.addEventListener("change", () => loadSkills(state.catSelect.value));
  loadSkills("");

  return modalHandle;
}

function createManageHeaderActions(cat, state, isClosed, onDeleted, onUpdated, onDataChanged, getModalHandle) {
  const fileInput = el("input", { type: "file", accept: ".csv", style: "display:none" });
  const actions = [];

  actions.push(el("button", {
    className: "btn btn-secondary btn-sm",
    onClick: () => openEditCatalogModal(cat, onUpdated, onDataChanged),
  }, el("span", { className: "material-symbols-outlined icon-16" }, "edit"), " Edit"));

  actions.push(el("button", {
    className: "btn btn-secondary btn-sm",
    onClick: () => fileInput.click(),
  }, el("span", { className: "material-symbols-outlined icon-16" }, "upload"), " Import CSV"));

  actions.push(el("button", {
    className: "btn btn-secondary btn-sm text-danger",
    onClick: async () => {
      if (!confirm(`Delete catalog "${cat.name}"? This cannot be undone.`)) return;
      try {
        await deleteSkillCatalog(cat.id);
        toast("Catalog deleted", "success");
        if (typeof onDataChanged === "function") await onDataChanged();
        if (typeof onDeleted === "function") onDeleted();
        getModalHandle()?.close();
      } catch (err) {
        toast(err.message, "error");
      }
    },
  }, el("span", { className: "material-symbols-outlined icon-16" }, "delete"), " Delete"));

  fileInput.onchange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      await toastForPromise(async () => {
        const buf = await file.arrayBuffer();
        await importSkillCatalogCsv(cat.id, buf);
        const refreshed = await getSkillCatalog(cat.id).catch(() => null);
        if (refreshed) {
          Object.assign(cat, refreshed);
          if (typeof onUpdated === "function") onUpdated(refreshed);
        }
        if (typeof onDataChanged === "function") await onDataChanged();
        if (!isClosed() && state.catSelect && state.skillsArea) {
          const categoriesFresh = await listSkillCategories(cat.id).catch(() => null);
          if (Array.isArray(categoriesFresh)) {
            const preferred = state.catSelect.value || state.currentCategory || "";
            repopulateCategorySelect(state.catSelect, categoriesFresh, preferred);
            state.currentCategory = state.catSelect.value || "";
          }
          state.skillsArea.innerHTML = "";
          state.skillsArea.appendChild(spinner());
          const skills = await listSkillDefinitions(cat.id, { page: 0, size: SKILLS_PAGE_SIZE, ...(state.currentCategory ? { category: state.currentCategory } : {}) });
          state.skillsArea.querySelector(".spinner")?.remove();
          upsertSkillsTable(state.skillsArea, skills || [], false);
        }
      }, {
        loadingMessage: "Importing CSV...",
        successMessage: "CSV imported successfully",
        errorMessage: (err) => err.message,
        minVisibleMs: 900,
      });
    } catch {
      // Errors surfaced by toastForPromise.
    }
  };

  actions.push(fileInput);
  return actions;
}

function openEditCatalogModal(cat, onUpdated, onDataChanged) {
  const form = el("div", { className: "stack-4" });
  const nameInput = buildInput("catalog-edit-name", "e.g. Company Skills Base", cat.name || "");
  const descInput = buildInput("catalog-edit-desc", "Optional description", cat.description || "");
  const sourceInput = buildInput("catalog-edit-source", "e.g. skills-base-2026", cat.source || "");

  form.appendChild(buildFormField("Name", "catalog-edit-name", nameInput));
  form.appendChild(buildFormField("Description", "catalog-edit-desc", descInput));
  form.appendChild(buildFormField("Source", "catalog-edit-source", sourceInput));

  modal("Edit Skill Catalog", form, {
    actions: [{
      label: "Save",
      className: "btn-primary",
      onClick: async () => {
        const name = nameInput.value.trim();
        if (!name) {
          toast("Name is required", "error");
          return;
        }
        try {
          const updated = await updateSkillCatalog(cat.id, {
            name,
            description: descInput.value.trim() || undefined,
            source: sourceInput.value.trim() || undefined,
            level_scale: cat.level_scale || undefined,
          });
          if (updated) {
            Object.assign(cat, updated);
            if (typeof onUpdated === "function") onUpdated(updated);
          }
          if (typeof onDataChanged === "function") await onDataChanged();
          toast("Catalog updated", "success");
        } catch (err) {
          toast(err.message, "error");
        }
      },
    }],
  });
}

function repopulateCategorySelect(selectEl, categories, preferredValue) {
  selectEl.innerHTML = "";
  selectEl.appendChild(el("option", { value: "" }, "All Categories"));
  (categories || []).forEach((category) => selectEl.appendChild(el("option", { value: category }, category)));
  selectEl.value = preferredValue && (categories || []).includes(preferredValue) ? preferredValue : "";
}

function upsertSkillsTable(skillsArea, rows, append) {
  const existingTbody = skillsArea.querySelector("tbody");
  if (existingTbody && append) {
    const pageTable = table(SKILL_DEFINITION_COLUMNS, rows);
    pageTable.querySelectorAll("tbody tr").forEach((tr) => existingTbody.appendChild(tr));
    return;
  }
  skillsArea.appendChild(table(SKILL_DEFINITION_COLUMNS, rows));
}

function buildInput(id, placeholder, value) {
  return el("input", {
    className: "form-input",
    id,
    name: id,
    type: "text",
    placeholder,
    value,
  });
}

function buildFormField(label, inputId, inputEl) {
  return el("div", { className: "form-group" },
    el("label", { className: "form-label", for: inputId }, label),
    inputEl
  );
}
