import { api } from "../api.js";
import { Auth } from "../auth.js";
import { el, spinner, emptyState, toast, modal, pageHeader, table } from "../ui.js";

export async function renderSkillsCatalog(container) {
  container.dataset.page = "skills";
  container.innerHTML = "";

  const user = Auth.getUser() || {};
  const canManage = user.entitlements?.can_manage_skills === true;

  const actions = [];
  if (canManage) {
    actions.push(el("button", {
      className: "btn btn-primary",
      onClick: () => showCreateCatalogModal(),
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:18px" }, "add"),
      el("span", {}, "Create Catalog")
    ));
  }

  container.appendChild(pageHeader("Skill Catalogs", "Browse and manage skill catalogs", actions));
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

  const catalogsArea = el("div", { className: "stack-6" });
  container.appendChild(catalogsArea);

  renderCatalogList(catalogsArea, catalogs, canManage, container);

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
            container.innerHTML = "";
            await renderSkillsCatalog(container);
          } catch (err) { toast(err.message, "error"); }
        },
      }],
    });
  }
}

function renderCatalogList(container, catalogs, canManage, rootContainer) {
  const grid = el("div", { className: "explore-grid" });

  for (const cat of catalogs) {
    const card = el("div", { className: "profile-card", style: "cursor:pointer" });

    const header = el("div", { className: "profile-card-header" });
    header.appendChild(el("h3", { className: "profile-card-title", style: "font-size:1rem" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple", style: "font-size:20px" }, "psychology"),
      cat.name
    ));
    if (cat.source) {
      header.appendChild(el("span", { className: "profile-prov-badge" }, cat.source));
    }
    card.appendChild(header);

    const body = el("div", { className: "profile-card-body" });
    if (cat.description) {
      body.appendChild(el("p", { className: "text-secondary", style: "margin:0 0 var(--space-3) 0" }, cat.description));
    }
    const stats = el("div", { className: "flex gap-4", style: "font-size:0.85rem" });
    stats.appendChild(el("span", {},
      el("strong", {}, String(cat.skill_count || 0)), " skills"
    ));
    if (cat.created_at) {
      stats.appendChild(el("span", { className: "text-secondary" },
        "Created " + new Date(cat.created_at).toLocaleDateString()
      ));
    }
    body.appendChild(stats);

    if (canManage) {
      const actions = el("div", { className: "flex gap-2", style: "margin-top:var(--space-3)" });

      const fileInput = el("input", { type: "file", accept: ".csv", style: "display:none" });
      fileInput.onchange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        try {
          toast("Importing CSV...", "info", 15000);
          const buf = await file.arrayBuffer();
          await api.post(`/api/v1/skills/${cat.id}/import`, buf);
          toast("CSV imported successfully", "success");
          location.hash = "#/skills";
          location.reload();
        } catch (err) { toast(err.message, "error"); }
      };
      actions.appendChild(fileInput);

      actions.appendChild(el("button", {
        className: "btn btn-secondary btn-sm",
        onClick: (e) => { e.stopPropagation(); fileInput.click(); },
      },
        el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, "upload"),
        " Import CSV"
      ));

      actions.appendChild(el("button", {
        className: "btn btn-secondary btn-sm",
        style: "color:var(--color-red-600)",
        onClick: async (e) => {
          e.stopPropagation();
          if (!confirm(`Delete catalog "${cat.name}"? This cannot be undone.`)) return;
          try {
            await api.delete(`/api/v1/skills/${cat.id}`);
            toast("Catalog deleted", "success");
            card.remove();
          } catch (err) { toast(err.message, "error"); }
        },
      },
        el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, "delete"),
        " Delete"
      ));

      body.appendChild(actions);
    }

    card.appendChild(body);

    card.addEventListener("click", () => {
      expandCatalogDetail(container, cat, catalogs, canManage, rootContainer);
    });

    grid.appendChild(card);
  }

  container.appendChild(grid);
}

async function expandCatalogDetail(parentContainer, cat, catalogs, canManage, rootContainer) {
  parentContainer.innerHTML = "";

  const backBtn = el("button", {
    className: "btn btn-ghost btn-sm",
    style: "margin-bottom:var(--space-4);display:inline-flex;align-items:center;gap:0.3rem",
    onClick: () => {
      parentContainer.innerHTML = "";
      renderCatalogList(parentContainer, catalogs, canManage, rootContainer);
    },
  }, "\u2190 Back to Catalogs");
  parentContainer.appendChild(backBtn);

  const header = el("div", { style: "margin-bottom:var(--space-6)" });
  header.appendChild(el("h2", { className: "page-title" }, cat.name));
  if (cat.description) header.appendChild(el("p", { className: "text-secondary" }, cat.description));
  parentContainer.appendChild(header);

  parentContainer.appendChild(spinner());

  let categories;
  try {
    categories = await api.get(`/api/v1/skills/${cat.id}/categories`);
  } catch (err) {
    parentContainer.querySelector(".spinner")?.remove();
    parentContainer.appendChild(emptyState("Could not load catalog details."));
    return;
  }

  parentContainer.querySelector(".spinner")?.remove();

  if (!categories?.length) {
    parentContainer.appendChild(emptyState("No skills in this catalog yet."));
    return;
  }

  const filterRow = el("div", { className: "flex gap-3", style: "margin-bottom:var(--space-4);align-items:center" });
  const catSelect = el("select", { className: "form-select", style: "max-width:300px" });
  catSelect.appendChild(el("option", { value: "" }, "All Categories"));
  categories.forEach((c) => catSelect.appendChild(el("option", { value: c }, c)));
  filterRow.appendChild(catSelect);
  parentContainer.appendChild(filterRow);

  const skillsArea = el("div", {});
  parentContainer.appendChild(skillsArea);

  async function loadSkills(category, page = 0) {
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
      skillsArea.querySelector(".spinner")?.remove();
      skillsArea.appendChild(emptyState("Error loading skills."));
      toast(err.message, "error");
    }
  }

  catSelect.addEventListener("change", () => loadSkills(catSelect.value));
  loadSkills("");
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
