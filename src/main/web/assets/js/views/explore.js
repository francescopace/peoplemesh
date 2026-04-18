import { api } from "../api.js";
import { el, spinner, toast, emptyState } from "../ui.js";
import { NODE_TYPE_ICONS, NODE_TYPE_COLORS } from "../node-types.js";
import { contactFooter } from "../contact-actions.js";
import { COUNTRIES } from "../utils/countries.js";

const NODE_TYPES = [
  { id: "",              label: "All Nodes",   icon: "layers" },
  { id: "PEOPLE",        label: "People",      icon: "person" },
  { id: "JOB",           label: "Jobs",        icon: "work" },
  { id: "COMMUNITY",     label: "Communities", icon: "groups" },
  { id: "EVENT",         label: "Events",      icon: "event" },
  { id: "PROJECT",       label: "Projects",    icon: "rocket_launch" },
  { id: "INTEREST_GROUP", label: "Groups",     icon: "interests" },
];

export async function renderExplore(container) {
  container.dataset.page = "explore";
  container.innerHTML = "";

  const header = el("header", { className: "explore-header" });
  header.appendChild(el("h1", { className: "page-title" }, "My Mesh"));
  header.appendChild(el("p", { className: "page-subtitle text-secondary" }, "Explore people, jobs, and communities in your mesh."));
  container.appendChild(header);

  /* === Filter bar === */
  const filterBar = el("div", { className: "explore-filter-bar" });

  /* Node type tabs */
  const hashParams = new URLSearchParams(window.location.hash.split("?")[1] || "");
  let activeType = hashParams.get("type") || "";
  const typeTabs = el("div", { className: "explore-type-tabs" });
  NODE_TYPES.forEach((t) => {
    const btn = el("button", {
      className: `explore-type-tab${t.id === activeType ? " active" : ""}`,
      dataset: { type: t.id },
    },
      el("span", { className: "material-symbols-outlined", style: "font-size:16px" }, t.icon),
      el("span", {}, t.label)
    );
    typeTabs.appendChild(btn);
  });
  /* Country select (same row as type tabs) */
  const countrySelect = el("select", { className: "form-select explore-country-select" });
  countrySelect.appendChild(el("option", { value: "" }, "All Countries"));
  COUNTRIES.forEach(([code, name]) =>
    countrySelect.appendChild(el("option", { value: code }, `${name} (${code})`))
  );
  countrySelect.addEventListener("change", () => loadResults());
  typeTabs.appendChild(countrySelect);

  filterBar.appendChild(typeTabs);

  /* Action buttons */
  const headerActions = el("div", { className: "explore-header-actions" });
  filterBar.appendChild(headerActions);

  container.appendChild(filterBar);

  /* Tab click handlers */
  typeTabs.addEventListener("click", (e) => {
    const tab = e.target.closest(".explore-type-tab");
    if (!tab) return;
    activeType = tab.dataset.type;
    typeTabs.querySelectorAll(".explore-type-tab").forEach((t) => t.classList.toggle("active", t.dataset.type === activeType));
    updateActions();
    loadResults();
  });

  const resultsArea = el("div", { className: "explore-results" });
  container.appendChild(resultsArea);

  function updateActions() {
    headerActions.innerHTML = "";
  }

  function updateTypeTabCounts(results) {
    const counts = {};
    (results || []).forEach((m) => {
      const key = (m.nodeType || "").toUpperCase();
      counts[key] = (counts[key] || 0) + 1;
    });
    typeTabs.querySelectorAll(".explore-type-tab").forEach((tab) => {
      const id = tab.dataset.type;
      const labelSpan = tab.querySelectorAll("span")[1];
      const def = NODE_TYPES.find((t) => t.id === id);
      if (!def || !labelSpan) return;
      const count = id === "" ? (results || []).length : (counts[id] || 0);
      labelSpan.textContent = count > 0 ? `${def.label} (${count})` : def.label;
    });
  }

  async function loadResults() {
    resultsArea.innerHTML = "";
    resultsArea.appendChild(spinner());

    await loadAllResults();
  }

  async function loadAllResults() {
    const query = {};
    const country = countrySelect.value; if (country) query.country = country;

    try {
      const matches = await api.get("/api/v1/matches/me", query);
      resultsArea.innerHTML = "";

      let filtered = matches || [];
      updateTypeTabCounts(filtered);
      if (activeType) {
        filtered = filtered.filter((m) => (m.nodeType || "").toUpperCase() === activeType);
      }
      if (!filtered.length) {
        resultsArea.appendChild(emptyState(
          el("span", {},
            "Your Mesh is empty. Update your ",
            el("a", { href: "#/profile" }, "profile"),
            " by adding skills or importing your CV."
          )
        ));
        return;
      }
      const grid = el("div", { className: "explore-grid" });
      filtered.forEach((m) => grid.appendChild(buildUnifiedCard(m)));
      resultsArea.appendChild(grid);
    } catch (err) {
      resultsArea.innerHTML = "";
      toast(err.message, "error");
      if (err.status === 204 || err.status === 404) {
        resultsArea.appendChild(emptyState(
          el("span", {},
            "To find better matches, update your ",
            el("a", { href: "#/profile" }, "profile"),
            " by adding skills or importing your CV."
          )
        ));
      }
    }
  }

  /* === Build cards === */

  function buildUnifiedCard(m) {
    const card = el("div", { className: "discover-card" });
    const nodeType = (m.nodeType || "UNKNOWN").toUpperCase();
    const isPerson = nodeType === "PEOPLE";
    const colors = NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.JOB;
    card.style.borderLeft = `3px solid ${colors.border}`;
    const iconName = NODE_TYPE_ICONS[nodeType] || "layers";

    const hdr = el("div", { className: "dc-header" });
    const avatarWrap = el("div", { className: "dc-avatar-wrap" });

    if (isPerson) {
      const fallbackEl = () => el("div", {
        className: "dc-avatar dc-avatar--icon",
        style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
      }, el("span", { className: "material-symbols-outlined" }, "person"));

      if (m.avatarUrl) {
        const img = el("img", {
          className: "dc-avatar dc-avatar--photo",
          src: m.avatarUrl,
          alt: m.title || "",
          referrerPolicy: "no-referrer",
        });
        img.onerror = () => img.replaceWith(fallbackEl());
        avatarWrap.appendChild(img);
      } else {
        avatarWrap.appendChild(fallbackEl());
      }
    } else {
      avatarWrap.appendChild(el("div", {
        className: "dc-avatar dc-avatar--icon",
        style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
      }, el("span", { className: "material-symbols-outlined" }, iconName)));
    }

    const info = el("div", { className: "dc-info" });
    const cardTitle = m.title || "Untitled";
    if (isPerson && m.id) {
      const nameLink = el("a", {
        href: `#/people/${m.id}`,
        className: "dc-name-link",
        style: "text-decoration:none;color:inherit",
      }, cardTitle);
      info.appendChild(el("h3", { className: "dc-name" }, nameLink));
    } else if (nodeType === "JOB" && m.id) {
      const jobLink = el("a", {
        href: `#/jobs/${m.id}`,
        className: "dc-name-link",
        style: "text-decoration:none;color:inherit",
      }, cardTitle);
      info.appendChild(el("h3", { className: "dc-name" }, jobLink));
    } else {
      info.appendChild(el("h3", { className: "dc-name" }, cardTitle));
    }
    const subtitle = el("div", { className: "dc-subtitle" });
    subtitle.appendChild(el("span", { className: "dc-type-badge", style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}` }, nodeType.replace("_", " ")));
    const locationParts = [m.person?.city, m.country].filter(Boolean);
    if (locationParts.length) {
      const geoMatch = m.breakdown?.geographyReason;
      const countryMatches = geoMatch === "same_country" || geoMatch === "same_continent" || geoMatch === "remote_friendly";
      const locStyle = countryMatches
        ? `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; padding:0.1rem 0.45rem; border-radius:4px; font-size:0.72rem`
        : "background:rgba(148,163,184,0.08); color:var(--color-gray-400); border:1px solid rgba(148,163,184,0.2); padding:0.1rem 0.45rem; border-radius:4px; font-size:0.72rem";
      subtitle.appendChild(el("span", { className: "dc-sep" }, "\u00B7"));
      subtitle.appendChild(el("span", { style: locStyle }, locationParts.join(", ")));
    }
    info.appendChild(subtitle);
    avatarWrap.appendChild(info);
    hdr.appendChild(avatarWrap);

    const score = Math.round((m.score || 0) * 100);
    hdr.appendChild(el("div", { className: "dc-score", style: `color:${colors.color}` }, `${score}%`));
    card.appendChild(hdr);

    if (isPerson && m.person) {
      const rolesText = m.person.roles?.length ? m.person.roles.join(", ") : m.person.skillsTechnical?.slice(0, 3).join(", ") || "";
      if (rolesText) card.appendChild(el("p", { className: "dc-meta" }, rolesText));
    }
    if (!isPerson && m.description) {
      const desc = m.description.length > 120 ? m.description.substring(0, 120) + "\u2026" : m.description;
      card.appendChild(el("p", { className: "dc-desc" }, desc));
    }

    const commonSet = new Set((m.breakdown?.commonItems || []).map((s) => s.toLowerCase()));
    const commonGoals = m.breakdown?.commonGoals || [];
    const geoReason = m.breakdown?.geographyReason;
    const GEO_POSITIVE = new Set(["same_country", "same_continent", "remote_friendly"]);
    const positiveGeo = geoReason && GEO_POSITIVE.has(geoReason);

    if (m.tags?.length || commonGoals.length || positiveGeo) {
      const matchStyle = `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; box-shadow:0 0 0 1px ${colors.border}`;
      const tagsArea = el("div", { className: "dc-tags-area" });
      const row = el("div", { className: "dc-tags" });
      (m.tags || []).slice(0, 8).forEach((t) => {
        const isCommon = commonSet.has(t.toLowerCase());
        row.appendChild(el("span", { className: "dc-tag", style: isCommon ? matchStyle : undefined }, t));
      });
      commonGoals.slice(0, 3).forEach((g) => row.appendChild(el("span", { className: "dc-tag", style: matchStyle }, g.replace(/_/g, " ").toLowerCase())));
      if (positiveGeo) {
        row.appendChild(el("span", { className: "dc-tag", style: matchStyle }, geoReason.replace(/_/g, " ")));
      }
      tagsArea.appendChild(row);
      card.appendChild(tagsArea);
    }

    if (isPerson) {
      const actions = contactFooter(
        m.person?.slackHandle,
        m.person?.email,
        m.person?.telegramHandle,
        m.person?.mobilePhone
      );
      if (actions) card.appendChild(actions);
    }

    return card;
  }

  updateActions();
  loadResults();
}

