import { api } from "../api.js";
import { Auth } from "../auth.js";
import { el, spinner, emptyState, toast, toastForPromise, modal, pageHeader, table } from "../ui.js";

export async function renderSkillsCatalog(container, options = {}) {
  const {
    showHeader = true,
    showCreateAction = true,
    onCreateActionReady = null,
    clearContainer = true,
    onDataChanged = null,
  } = options;
  container.dataset.page = "skills";
  if (clearContainer) {
    container.innerHTML = "";
  }

  const user = Auth.getUser() || {};
  const canManage = user.entitlements?.can_manage_skills === true;
  let openCreateCatalogModal = () => {};

  const actions = [];
  if (canManage && showCreateAction) {
    actions.push(el("button", {
      className: "btn btn-primary",
      onClick: () => openCreateCatalogModal(),
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:18px" }, "add"),
      el("span", {}, "Create Catalog")
    ));
  }

  if (showHeader) {
    container.appendChild(pageHeader("Skill Catalogs", "Browse and manage skill catalogs", actions));
  }
  container.appendChild(spinner());

  let catalogs;
  try {
    catalogs = await api.get("/api/v1/skills");
  } catch (err) {
    container.querySelector(".spinner")?.remove();
    container.appendChild(emptyState("Could not load catalogs."));
    toast(err.message, "error");
    return;
  }

  container.querySelector(".spinner")?.remove();

  if (!catalogs?.length) {
    container.appendChild(emptyState(
      canManage ? "No skill catalogs yet. Create one to get started." : "No skill catalogs available.",
      canManage ? "Create Catalog" : null,
      canManage ? () => showCreateCatalogModal() : null
    ));
    return;
  }

  const catalogsArea = el("div", { className: "stack-6 skills-catalogs-content" });
  container.appendChild(catalogsArea);

  renderCatalogList(catalogsArea, catalogs, canManage, onDataChanged);
  openCreateCatalogModal = showCreateCatalogModal;
  if (canManage && typeof onCreateActionReady === "function") {
    onCreateActionReady(openCreateCatalogModal);
  }

  async function showCreateCatalogModal() {
    const form = el("div", { className: "stack-4" });
    form.appendChild(formGroup("Name", "catalog-name", "text", "e.g. Company Skills Base"));
    form.appendChild(formGroup("Description", "catalog-desc", "text", "Optional description"));
    form.appendChild(formGroup("Source", "catalog-source", "text", "e.g. skills-base-2026"));

    modal("Create Skill Catalog", form, {
      actions: [{
        label: "Create",
        className: "btn-primary",
        onClick: async () => {
          const name = form.querySelector("#catalog-name").value.trim();
          if (!name) { toast("Name is required", "error"); return; }
          try {
            await api.post("/api/v1/skills", {
              name,
              description: form.querySelector("#catalog-desc").value.trim() || undefined,
              source: form.querySelector("#catalog-source").value.trim() || undefined,
            });
            toast("Catalog created", "success");
            if (typeof onDataChanged === "function") {
              await onDataChanged();
            }
            if (clearContainer) {
              container.innerHTML = "";
            } else {
              container.querySelector(".skills-catalogs-content")?.remove();
            }
            await renderSkillsCatalog(container, options);
          } catch (err) { toast(err.message, "error"); }
        },
      }],
    });
  }
}

function renderCatalogList(container, catalogs, canManage, onDataChanged) {
  const grid = el("div", { className: "explore-grid" });

  for (const cat of catalogs) {
    const card = el("div", { className: "profile-card", style: "cursor:pointer" });

    const header = el("div", { className: "profile-card-header" });
    const titleText = el("span", {}, cat.name);
    header.appendChild(el("h3", { className: "profile-card-title", style: "font-size:1rem" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple", style: "font-size:20px" }, "psychology"),
      titleText
    ));
    let sourceBadge = null;
    if (cat.source) {
      sourceBadge = el("span", { className: "profile-prov-badge" }, cat.source);
      header.appendChild(sourceBadge);
    }
    card.appendChild(header);

    const body = el("div", { className: "profile-card-body" });
    let descEl = null;
    if (cat.description) {
      descEl = el("p", { className: "text-secondary", style: "margin:0 0 var(--space-3) 0" }, cat.description);
      body.appendChild(descEl);
    }
    const stats = el("div", { className: "flex gap-4", style: "font-size:0.85rem" });
    const countValue = el("strong", {}, String(cat.skill_count || 0));
    stats.appendChild(el("span", {},
      countValue, " skills"
    ));
    if (cat.created_at) {
      stats.appendChild(el("span", { className: "text-secondary" },
        "Created " + new Date(cat.created_at).toLocaleDateString()
      ));
    }
    body.appendChild(stats);

    card.appendChild(body);

    card.addEventListener("click", () => {
      openCatalogDetailModal(cat, canManage, () => card.remove(), (updated) => {
        Object.assign(cat, updated);
        titleText.textContent = cat.name || "";
        countValue.textContent = String(cat.skill_count || 0);

        if (cat.description) {
          if (!descEl) {
            descEl = el("p", { className: "text-secondary", style: "margin:0 0 var(--space-3) 0" }, cat.description);
            body.insertBefore(descEl, stats);
          } else {
            descEl.textContent = cat.description;
          }
        } else if (descEl) {
          descEl.remove();
          descEl = null;
        }

        if (cat.source) {
          if (!sourceBadge) {
            sourceBadge = el("span", { className: "profile-prov-badge" }, cat.source);
            header.appendChild(sourceBadge);
          } else {
            sourceBadge.textContent = cat.source;
          }
        } else if (sourceBadge) {
          sourceBadge.remove();
          sourceBadge = null;
        }
      }, onDataChanged);
    });

    grid.appendChild(card);
  }

  container.appendChild(grid);
}

async function openCatalogDetailModal(cat, canManage, onDeleted, onUpdated, onDataChanged) {
  const content = el("div", { className: "stack-4" });
  let modalHandle = null;
  const headerActions = [];
  let catSelect = null;
  let currentCategory = "";
  let skillsArea = null;

  if (canManage) {
    headerActions.push(el("button", {
      className: "btn btn-secondary btn-sm",
      onClick: () => openEditCatalogModal(),
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, "edit"),
      " Edit"
    ));

    const fileInput = el("input", { type: "file", accept: ".csv", style: "display:none" });
    content.appendChild(fileInput);
    fileInput.onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      try {
        await toastForPromise(async () => {
          const buf = await file.arrayBuffer();
          await api.post(`/api/v1/skills/${cat.id}/import`, buf);
          const refreshed = await api.get(`/api/v1/skills/${cat.id}`).catch(() => null);
          if (refreshed) {
            Object.assign(cat, refreshed);
            if (typeof onUpdated === "function") onUpdated(refreshed);
          }
          if (typeof onDataChanged === "function") {
            await onDataChanged();
          }
          if (!closed && catSelect && skillsArea) {
            const categoriesFresh = await api.get(`/api/v1/skills/${cat.id}/categories`).catch(() => null);
            if (Array.isArray(categoriesFresh)) {
              const preferred = catSelect.value || currentCategory || "";
              repopulateCategorySelect(catSelect, categoriesFresh, preferred);
              currentCategory = catSelect.value || "";
            }
            await loadSkills(currentCategory, 0);
          }
        }, {
          loadingMessage: "Importing CSV...",
          successMessage: "CSV imported successfully",
          errorMessage: (err) => err.message,
          minVisibleMs: 900,
        });
      } catch {
        // Errors are surfaced by toastForPromise.
      }
    };

    headerActions.push(el("button", {
      className: "btn btn-secondary btn-sm",
      onClick: () => fileInput.click(),
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, "upload"),
      " Import CSV"
    ));
    headerActions.push(el("button", {
      className: "btn btn-secondary btn-sm",
      style: "color:var(--color-red-600)",
      onClick: async () => {
        if (!confirm(`Delete catalog "${cat.name}"? This cannot be undone.`)) return;
        try {
          await api.delete(`/api/v1/skills/${cat.id}`);
          toast("Catalog deleted", "success");
          if (typeof onDataChanged === "function") {
            await onDataChanged();
          }
          if (typeof onDeleted === "function") onDeleted();
          if (modalHandle) modalHandle.close();
        } catch (err) {
          toast(err.message, "error");
        }
      },
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, "delete"),
      " Delete"
    ));
  }

  if (cat.description) {
    content.appendChild(el("p", { className: "text-secondary", style: "margin:0" }, cat.description));
  }
  content.appendChild(spinner());

  let closed = false;
  modalHandle = modal(`Catalog: ${cat.name}`, content, {
    dialogClassName: "catalog-detail-dialog",
    headerActions,
    onClose: () => {
      closed = true;
    },
  });

  let categories;
  try {
    categories = await api.get(`/api/v1/skills/${cat.id}/categories`);
  } catch (err) {
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

  const filterRow = el("div", { className: "flex gap-3", style: "margin-bottom:var(--space-4);align-items:center" });
  catSelect = el("select", { className: "form-select", style: "max-width:300px" });
  repopulateCategorySelect(catSelect, categories, "");
  filterRow.appendChild(catSelect);
  content.appendChild(filterRow);

  skillsArea = el("div", {});
  content.appendChild(skillsArea);

  async function loadSkills(category, page = 0) {
    if (closed) return;
    currentCategory = category || "";
    if (page === 0) {
      skillsArea.innerHTML = "";
      skillsArea.appendChild(spinner());
    } else {
      skillsArea.querySelector(".load-more-btn")?.remove();
    }

    try {
      const query = { page, size: 50 };
      if (category) query.category = category;
      const skills = await api.get(`/api/v1/skills/${cat.id}/definitions`, query);
      if (closed) return;
      skillsArea.querySelector(".spinner")?.remove();

      if (!skills?.length) {
        if (page === 0) skillsArea.appendChild(emptyState("No skills found for this filter."));
        return;
      }

      let existingTbody = skillsArea.querySelector("tbody");
      if (existingTbody && page > 0) {
        const cols = [
          { key: "name", label: "Skill" },
          { key: "category", label: "Category" },
          { key: "aliases", label: "Aliases", render: (v) => (v || []).join(", ") || "\u2014" },
        ];
        for (const row of skills) {
          const tr = document.createElement("tr");
          for (const col of cols) {
            const td = document.createElement("td");
            td.textContent = col.render ? col.render(row[col.key]) : (row[col.key] ?? "\u2014");
            tr.appendChild(td);
          }
          existingTbody.appendChild(tr);
        }
      } else {
        const tbl = table(
          [
            { key: "name", label: "Skill" },
            { key: "category", label: "Category" },
            { key: "aliases", label: "Aliases", render: (v) => (v || []).join(", ") || "\u2014" },
          ],
          skills,
        );
        skillsArea.appendChild(tbl);
      }

      if (skills.length >= 50) {
        const moreBtn = el("button", {
          className: "btn btn-secondary btn-sm load-more-btn",
          style: "margin-top:var(--space-3)",
          onClick: () => loadSkills(category, page + 1),
        }, "Load More");
        skillsArea.appendChild(moreBtn);
      }
    } catch (err) {
      if (closed) return;
      skillsArea.querySelector(".spinner")?.remove();
      skillsArea.appendChild(emptyState("Error loading skills."));
      toast(err.message, "error");
    }
  }

  catSelect.addEventListener("change", () => loadSkills(catSelect.value));
  loadSkills("");

  function openEditCatalogModal() {
    const form = el("div", { className: "stack-4" });
    const nameInput = el("input", {
      className: "form-input",
      id: "catalog-edit-name",
      name: "catalog-edit-name",
      type: "text",
      placeholder: "e.g. Company Skills Base",
      value: cat.name || "",
    });
    const descInput = el("input", {
      className: "form-input",
      id: "catalog-edit-desc",
      name: "catalog-edit-desc",
      type: "text",
      placeholder: "Optional description",
      value: cat.description || "",
    });
    const sourceInput = el("input", {
      className: "form-input",
      id: "catalog-edit-source",
      name: "catalog-edit-source",
      type: "text",
      placeholder: "e.g. skills-base-2026",
      value: cat.source || "",
    });

    form.appendChild(el("div", { className: "form-group" },
      el("label", { className: "form-label", for: "catalog-edit-name" }, "Name"),
      nameInput
    ));
    form.appendChild(el("div", { className: "form-group" },
      el("label", { className: "form-label", for: "catalog-edit-desc" }, "Description"),
      descInput
    ));
    form.appendChild(el("div", { className: "form-group" },
      el("label", { className: "form-label", for: "catalog-edit-source" }, "Source"),
      sourceInput
    ));

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
            const updated = await api.put(`/api/v1/skills/${cat.id}`, {
              name,
              description: descInput.value.trim() || undefined,
              source: sourceInput.value.trim() || undefined,
              level_scale: cat.level_scale || undefined,
            });
            if (updated) {
              Object.assign(cat, updated);
              if (typeof onUpdated === "function") onUpdated(updated);
            }
            if (typeof onDataChanged === "function") {
              await onDataChanged();
            }
            toast("Catalog updated", "success");
          } catch (err) {
            toast(err.message, "error");
          }
        },
      }],
    });
  }
}

function repopulateCategorySelect(selectEl, categories, preferredValue) {
  selectEl.innerHTML = "";
  selectEl.appendChild(el("option", { value: "" }, "All Categories"));
  (categories || []).forEach((c) => selectEl.appendChild(el("option", { value: c }, c)));
  if (preferredValue && (categories || []).includes(preferredValue)) {
    selectEl.value = preferredValue;
  } else {
    selectEl.value = "";
  }
}

function formGroup(label, id, type, placeholder) {
  const group = el("div", { className: "form-group" });
  group.appendChild(el("label", { className: "form-label", for: id }, label));
  group.appendChild(el("input", {
    className: "form-input", type, id, name: id,
    placeholder: placeholder || "",
  }));
  return group;
}
