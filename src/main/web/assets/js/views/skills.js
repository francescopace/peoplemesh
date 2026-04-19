import { Auth } from "../auth.js";
import { el, spinner, emptyState, toast, modal, pageHeader } from "../ui.js";
import {
  createSkillCatalog,
  listSkillCatalogs,
} from "../services/skills-service.js";
import { openSkillsCatalogDetailModal } from "../components/skills-catalog-detail-modal.js";

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
  const canManage = user.entitlements?.is_admin === true;
  let openCreateCatalogModal = () => {};

  const actions = [];
  if (canManage && showCreateAction) {
    actions.push(el("button", {
      className: "btn btn-primary",
      onClick: () => openCreateCatalogModal(),
    },
      el("span", { className: "material-symbols-outlined icon-18" }, "add"),
      el("span", {}, "Create Catalog")
    ));
  }

  if (showHeader) {
    container.appendChild(pageHeader("Skill Catalogs", "Browse and manage skill catalogs", actions));
  }
  container.appendChild(spinner());

  let catalogs;
  try {
    catalogs = await listSkillCatalogs();
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
            await createSkillCatalog({
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
    const card = el("div", { className: "profile-card cursor-pointer" });

    const header = el("div", { className: "profile-card-header" });
    const titleText = el("span", {}, cat.name);
    header.appendChild(el("h3", { className: "profile-card-title profile-card-title-sm" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple icon-20" }, "psychology"),
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
      descEl = el("p", { className: "text-secondary skills-catalog-desc" }, cat.description);
      body.appendChild(descEl);
    }
    const stats = el("div", { className: "flex gap-4 skills-catalog-stats" });
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
      openSkillsCatalogDetailModal(cat, canManage, () => card.remove(), (updated) => {
        Object.assign(cat, updated);
        titleText.textContent = cat.name || "";
        countValue.textContent = String(cat.skill_count || 0);

        if (cat.description) {
          if (!descEl) {
            descEl = el("p", { className: "text-secondary skills-catalog-desc" }, cat.description);
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

function formGroup(label, id, type, placeholder) {
  const group = el("div", { className: "form-group" });
  group.appendChild(el("label", { className: "form-label", for: id }, label));
  group.appendChild(el("input", {
    className: "form-input", type, id, name: id,
    placeholder: placeholder || "",
  }));
  return group;
}
