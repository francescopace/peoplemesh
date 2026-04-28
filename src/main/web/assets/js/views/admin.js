import { Auth } from "../auth.js";
import { el, emptyState, modal, spinner, toast } from "../ui.js";
import { renderSkillsCatalogPanel } from "../components/skills-catalog-panel.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import { getSystemStatistics } from "../services/admin-service.js";

export async function renderAdmin(container) {
  container.dataset.page = "admin";
  container.innerHTML = "";

  const user = Auth.getUser() || {};
  if (!user.entitlements?.is_admin) {
    container.appendChild(emptyState("You do not have permission to access this page."));
    return;
  }

  const overviewSection = el("section", { className: "stack-4" });
  container.appendChild(overviewSection);
  await renderAdminOverview(overviewSection);
}

async function renderAdminOverview(container) {
  container.innerHTML = "";
  container.appendChild(el("h2", { className: "page-title", style: "font-size:1.1rem" }, "Data Overview"));
  container.appendChild(spinner());

  let overview;
  try {
    overview = await getSystemStatistics();
  } catch (err) {
    container.querySelector(".spinner")?.remove();
    container.appendChild(emptyState("Could not load data overview."));
    toast(getUserFacingErrorMessage(err, "Could not load data overview."), "error");
    return;
  }

  container.querySelector(".spinner")?.remove();

  const metrics = [
    { label: "Users", value: overview?.users ?? 0, icon: "person" },
    { label: "Jobs", value: overview?.jobs ?? 0, icon: "work" },
    { label: "Others", value: overview?.others ?? 0, icon: "groups" },
    { label: "Skills", value: overview?.skills ?? 0, icon: "psychology", interactive: true },
  ];
  const validPercent = computeValidPercent(
    Number(overview?.searchableNodesWithEmbedding ?? 0),
    Number(overview?.searchableNodes ?? 0)
  );
  const isCoverageDegraded = validPercent < 100;
  const timingMetrics = [
    {
      label: "LLM Inference Time",
      icon: "smart_toy",
      stats: overview?.timings?.llmInference,
    },
    {
      label: "HNSW Search Time",
      icon: "travel_explore",
      stats: overview?.timings?.hnswSearch,
    },
    {
      label: "Embedding Time",
      icon: "neurology",
      stats: overview?.timings?.embeddingInferenceSingle,
    },
    {
      label: "Embedding Status",
      icon: "hub",
      value: formatPercent(validPercent),
      details: `${Number(overview?.searchableNodesWithEmbedding ?? 0).toLocaleString()} / ${Number(overview?.searchableNodes ?? 0).toLocaleString()} searchable nodes`,
      isDegraded: isCoverageDegraded,
    },
  ];
  const dataGrid = el("div", { className: "admin-stats-grid" });
  for (const metric of metrics) {
    const card = el("div", { className: `profile-card admin-stat-card${metric.interactive ? " admin-stat-card--clickable" : ""}` });
    if (metric.interactive) {
      card.setAttribute("role", "button");
      card.setAttribute("tabindex", "0");
      card.setAttribute("aria-label", "Open skills list");
      card.addEventListener("click", () => openSkillsModal(container));
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openSkillsModal(container);
        }
      });
    }
    const header = el("div", { className: "profile-card-header" });
    header.appendChild(el("h3", { className: "profile-card-title", style: "font-size:1rem" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple", style: "font-size:20px" }, metric.icon),
      metric.label
    ));
    if (metric.interactive) {
      header.appendChild(el("span", { className: "material-symbols-outlined admin-stat-open-icon", "aria-hidden": "true" }, "open_in_new"));
    }
    card.appendChild(header);

    const body = el("div", { className: "profile-card-body" });
    body.appendChild(el("p", { style: "font-size:1.5rem;font-weight:700;margin:0" }, Number(metric.value || 0).toLocaleString()));
    if (metric.interactive) {
      body.appendChild(el("p", { className: "admin-stat-hint" }, "Click to open the skills dictionary"));
    }
    card.appendChild(body);
    dataGrid.appendChild(card);
  }
  container.appendChild(dataGrid);

  container.appendChild(el("h2", { className: "page-title", style: "font-size:1.1rem;margin-top:var(--space-8)" }, "Inference Statistics"));

  const timingGrid = el("div", { className: "admin-stats-grid" });
  for (const metric of timingMetrics) {
    const card = el("div", { className: "profile-card admin-stat-card" });
    const header = el("div", { className: "profile-card-header" });
    header.appendChild(el("h3", { className: "profile-card-title", style: "font-size:1rem" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--purple", style: "font-size:20px" }, metric.icon),
      metric.label
    ));
    card.appendChild(header);

    const body = el("div", { className: "profile-card-body" });
    if (metric.stats) {
      body.appendChild(el("p", { style: "font-size:1.35rem;font-weight:700;margin:0" }, formatLatency(metric.stats?.avgMs)));
      body.appendChild(el("p", { className: "admin-stat-details" },
        `p95 ${formatLatency(metric.stats?.p95Ms)} · max ${formatLatency(metric.stats?.maxMs)} · n ${(metric.stats?.sampleCount ?? 0).toLocaleString()}`
      ));
    } else {
      body.appendChild(el(
        "p",
        { className: metric.isDegraded ? "text-danger" : "", style: "font-size:1.35rem;font-weight:700;margin:0" },
        metric.value || "0.00%"
      ));
      body.appendChild(el(
        "p",
        { className: "admin-stat-details" },
        metric.details || ""
      ));
    }
    card.appendChild(body);
    timingGrid.appendChild(card);
  }

  container.appendChild(timingGrid);
}

function openSkillsModal(overviewContainer) {
  const headerActions = el("div", { className: "skills-modal-header-actions" });
  const headerButtons = el("div", { className: "flex gap-2", style: "align-items:center;flex-wrap:wrap" });
  headerActions.appendChild(headerButtons);
  const content = el("div", { className: "skills-modal-content" });
  modal("Skills", content, {
    dialogClassName: "modal-dialog--skills",
    headerActions: [headerActions],
    onClose: () => {
      headerActions.replaceChildren();
    },
  });
  renderSkillsCatalogPanel(content, {
    clearContainer: true,
    wrapInCard: false,
    actionsContainer: headerButtons,
    onDataChanged: async () => {
      await renderAdminOverview(overviewContainer);
    },
  });
}

function formatLatency(value) {
  const ms = Number(value || 0);
  return `${ms.toLocaleString()} ms`;
}

function formatPercent(value) {
  return `${Number(value || 0).toFixed(2)}%`;
}

function computeValidPercent(withEmbedding, totalSearchable) {
  if (totalSearchable <= 0) {
    return 100;
  }
  return (withEmbedding * 100) / totalSearchable;
}
