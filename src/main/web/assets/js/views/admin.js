import { Auth } from "../auth.js";
import { api } from "../api.js";
import { el, emptyState, pageHeader, spinner, toast } from "../ui.js";
import { renderSkillsCatalog } from "./skills.js";

export async function renderAdmin(container) {
  container.dataset.page = "admin";
  container.innerHTML = "";

  const user = Auth.getUser() || {};
  if (!user.entitlements?.can_manage_skills) {
    container.appendChild(emptyState("You do not have permission to access this page."));
    return;
  }

  let openCreateCatalogModal = null;
  const createCatalogBtn = el("button", {
    className: "btn btn-primary",
    disabled: "true",
    onClick: () => {
      if (openCreateCatalogModal) {
        openCreateCatalogModal();
      }
    },
  },
    el("span", { className: "material-symbols-outlined", style: "font-size:18px" }, "add"),
    el("span", {}, "Create Catalog")
  );

  container.appendChild(pageHeader("Administration", "Manage skill catalogs", [createCatalogBtn]));

  const overviewSection = el("section", { className: "stack-4" });
  container.appendChild(overviewSection);

  const catalogsSection = el("section", { className: "stack-4", style: "margin-top:var(--space-8)" });
  catalogsSection.appendChild(el("h2", { className: "page-title", style: "font-size:1.1rem" }, "Skill Catalogs"));
  container.appendChild(catalogsSection);

  const overviewPromise = renderAdminOverview(overviewSection);
  await renderSkillsCatalog(catalogsSection, {
    showHeader: false,
    showCreateAction: false,
    clearContainer: false,
    onDataChanged: async () => {
      await renderAdminOverview(overviewSection);
    },
    onCreateActionReady: (openFn) => {
      openCreateCatalogModal = openFn;
      createCatalogBtn.removeAttribute("disabled");
    },
  });
  await overviewPromise;
}

async function renderAdminOverview(container) {
  container.innerHTML = "";
  container.appendChild(el("h2", { className: "page-title", style: "font-size:1.1rem" }, "Data Overview"));
  container.appendChild(spinner());

  let overview;
  try {
    overview = await api.get("/api/v1/system/statistics");
  } catch (err) {
    container.querySelector(".spinner")?.remove();
    container.appendChild(emptyState("Could not load data overview."));
    toast(err.message, "error");
    return;
  }

  container.querySelector(".spinner")?.remove();

  const metrics = [
    { label: "Users", value: overview?.users ?? 0, icon: "person" },
    { label: "Jobs", value: overview?.jobs ?? 0, icon: "work" },
    { label: "Groups", value: overview?.groups ?? 0, icon: "groups" },
    { label: "Skills", value: overview?.skills ?? 0, icon: "psychology" },
  ];

  const grid = el("div", { className: "admin-stats-grid" });
  for (const metric of metrics) {
    const card = el("div", { className: "profile-card admin-stat-card" });
    const header = el("div", { className: "profile-card-header" });
    header.appendChild(el("h3", { className: "profile-card-title", style: "font-size:1rem" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple", style: "font-size:20px" }, metric.icon),
      metric.label
    ));
    card.appendChild(header);

    const body = el("div", { className: "profile-card-body" });
    body.appendChild(el("p", { style: "font-size:1.5rem;font-weight:700;margin:0" }, Number(metric.value || 0).toLocaleString()));
    card.appendChild(body);
    grid.appendChild(card);
  }

  container.appendChild(grid);
}
