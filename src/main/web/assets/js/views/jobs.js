import { api } from "../api.js";
import { el, spinner, toast, emptyState } from "../ui.js";

function jobFromNodeDto(node) {
  if (!node || (node.nodeType || "").toUpperCase() !== "JOB") return null;
  const sd = node.structuredData || {};
  const tags = node.tags || [];
  const fromSd = sd.skills_required || sd.skillsRequired;
  const skillsRequired = tags.length ? tags : (Array.isArray(fromSd) ? fromSd : []);
  return {
    id: node.id,
    title: node.title,
    description: node.description,
    company: sd.company || sd.employer || "",
    location: sd.location || [sd.city, node.country].filter(Boolean).join(", ") || node.country || "",
    workMode: sd.work_mode || sd.workMode,
    employmentType: sd.employment_type || sd.employmentType,
    publishedAt: node.createdAt,
    createdAt: node.createdAt,
    skillsRequired,
    externalUrl: sd.external_url || sd.externalUrl,
  };
}

function meshMatchToCandidate(m) {
  const p = m.person || {};
  return {
    nodeId: m.id,
    displayName: m.title,
    avatarUrl: m.avatarUrl,
    roles: p.roles,
    country: m.country,
    score: m.score,
    compositeScore: m.score,
    skillsTechnical: p.skillsTechnical,
    toolsAndTech: p.toolsAndTech,
    seniority: p.seniority,
    workMode: p.workMode,
  };
}

export async function renderJobDetail(container, { id }) {
  container.dataset.page = "jobs";
  container.innerHTML = "";
  container.appendChild(spinner());

  try {
    const node = await api.get(`/api/v1/nodes/${id}`);
    container.querySelector(".spinner")?.remove();

    const job = jobFromNodeDto(node);
    if (!job) { container.appendChild(emptyState("Job not found")); return; }

    const header = el("header", { className: "jobs-header" });
    const headerText = el("div", {});
    headerText.appendChild(el("h1", { className: "page-title" }, job.title || "Untitled"));
    headerText.appendChild(el("p", { className: "page-subtitle text-secondary" }, `${job.company || ""} ${job.location || "Job Position"}`));
    header.appendChild(headerText);

    container.appendChild(header);

    /* Info card */
    const infoCard = el("div", { className: "profile-card" });
    const infoHeader = el("div", { className: "profile-card-header" });
    infoHeader.appendChild(el("h2", { className: "profile-card-title" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--blue" }, "work"),
      "Position Details"
    ));
    infoCard.appendChild(infoHeader);

    const infoBody = el("div", { className: "profile-card-body" });
    const fields = el("div", { className: "profile-field-row profile-field-row--2" });
    fields.appendChild(detailField("Published", formatDate(job.publishedAt || job.createdAt)));
    fields.appendChild(detailField("Work Mode", job.workMode));
    infoBody.appendChild(fields);

    if (job.employmentType) {
      const fields2 = el("div", { className: "profile-field-row profile-field-row--2", style: "margin-top:var(--space-6)" });
      fields2.appendChild(detailField("Employment Type", job.employmentType));
      infoBody.appendChild(fields2);
    }

    if (job.description) {
      const descBlock = el("div", { style: "margin-top:var(--space-6)" });
      descBlock.appendChild(el("div", { className: "profile-field-label" }, "Description"));
      descBlock.appendChild(el("p", { className: "text-sm", style: "margin-top:0.25rem;line-height:1.6" }, job.description));
      infoBody.appendChild(descBlock);
    }
    if (job.skillsRequired?.length) {
      const skillsBlock = el("div", { className: "profile-tag-group", style: "margin-top:var(--space-6)" });
      skillsBlock.appendChild(el("div", { className: "profile-field-label" }, "Required Skills"));
      const tags = el("div", { className: "profile-tags" });
      job.skillsRequired.forEach((s) => tags.appendChild(el("span", { className: "profile-tag profile-tag--blue" }, s)));
      skillsBlock.appendChild(tags);
      infoBody.appendChild(skillsBlock);
    }
    if (job.externalUrl) {
      const applyBlock = el("div", { style: "margin-top:var(--space-6)" });
      applyBlock.appendChild(el("a", {
        href: job.externalUrl,
        target: "_blank",
        rel: "noopener",
        className: "btn btn-primary",
      },
        el("span", { className: "material-symbols-outlined icon-18" }, "open_in_new"),
        "Apply on Workday"
      ));
      infoBody.appendChild(applyBlock);
    }
    infoCard.appendChild(infoBody);
    container.appendChild(infoCard);

    /* Candidates */
    const candSection = el("div", { style: "margin-top: var(--space-8)" });
    candSection.appendChild(el("h2", { style: "font-size:1.15rem;font-weight:700;margin-bottom:var(--space-4)" }, "Best Matches"));
    candSection.appendChild(spinner());
    container.appendChild(candSection);

    try {
      const raw = await api.get(`/api/v1/matches/${id}`, { type: "PEOPLE" });
      const candidates = Array.isArray(raw) ? raw.map(meshMatchToCandidate) : [];
      candSection.querySelector(".spinner")?.remove();
      if (candidates?.length) {
        const grid = el("div", { className: "jobs-match-grid" });
        candidates.forEach((c) => {
          const mc = el("div", { className: "jmc" });
          const score = Math.round((c.score || c.compositeScore || 0) * 100);

          /* === Top: avatar + identity + score === */
          const top = el("div", { className: "jmc-top" });

          const fallbackAvatar = () => el("div", {
            className: "jmc-avatar jmc-avatar--fallback",
          }, el("span", { className: "material-symbols-outlined" }, "person"));

          if (c.avatarUrl) {
            const img = el("img", {
              className: "jmc-avatar jmc-avatar--photo",
              src: c.avatarUrl,
              alt: c.displayName || "",
              referrerPolicy: "no-referrer",
            });
            img.onerror = () => img.replaceWith(fallbackAvatar());
            top.appendChild(img);
          } else {
            top.appendChild(fallbackAvatar());
          }

          const info = el("div", { className: "jmc-info" });
          const nameText = c.displayName || "Anonymous";
          if (c.nodeId) {
            info.appendChild(el("h3", { className: "jmc-name" },
              el("a", { href: `#/people/${c.nodeId}`, className: "jmc-name-link" }, nameText)));
          } else {
            info.appendChild(el("h3", { className: "jmc-name" }, nameText));
          }

          const meta = el("div", { className: "jmc-meta" });
          if (c.roles?.length) {
            meta.appendChild(el("span", {}, c.roles.join(", ")));
          }
          if (c.country) {
            if (c.roles?.length) meta.appendChild(el("span", { className: "jmc-sep" }, "\u00B7"));
            meta.appendChild(el("span", {},
              el("span", { className: "material-symbols-outlined jmc-meta-icon" }, "location_on"),
              c.country
            ));
          }
          if (meta.children.length) info.appendChild(meta);
          top.appendChild(info);

          const scoreEl = el("div", { className: `jmc-score ${score >= 70 ? "jmc-score--high" : score >= 50 ? "jmc-score--mid" : ""}` }, `${score}%`);
          top.appendChild(scoreEl);
          mc.appendChild(top);

          /* === Badges row: seniority + work mode === */
          const badges = el("div", { className: "jmc-badges" });
          if (c.seniority) badges.appendChild(el("span", { className: "jmc-badge jmc-badge--seniority" }, c.seniority));
          if (c.workMode) badges.appendChild(el("span", { className: "jmc-badge" }, c.workMode.replace(/_/g, " ")));
          if (badges.children.length) mc.appendChild(badges);

          /* === Skills tags === */
          const allSkills = [...new Set([...(c.skillsTechnical || []), ...(c.toolsAndTech || [])])];
          if (allSkills.length) {
            const tagsWrap = el("div", { className: "jmc-tags" });
            allSkills.slice(0, 6).forEach((s) =>
              tagsWrap.appendChild(el("span", { className: "jmc-tag" }, s)));
            if (allSkills.length > 6) {
              tagsWrap.appendChild(el("span", { className: "jmc-tag jmc-tag--more" }, `+${allSkills.length - 6}`));
            }
            mc.appendChild(tagsWrap);
          }

          /* === Footer actions === */
          if (c.nodeId) {
            const foot = el("div", { className: "jmc-footer" });
            foot.appendChild(el("a", {
              href: `#/people/${c.nodeId}`,
              className: "jmc-view-link",
            },
              "View Profile",
              el("span", { className: "material-symbols-outlined" }, "arrow_forward")
            ));
            mc.appendChild(foot);
          }

          grid.appendChild(mc);
        });
        candSection.appendChild(grid);
      } else {
        candSection.appendChild(el("p", { className: "text-sm text-secondary" }, "No matching candidates found."));
      }
    } catch (candErr) {
      candSection.querySelector(".spinner")?.remove();
      candSection.appendChild(el("p", { className: "text-sm text-secondary" }, "Could not load candidates."));
    }
  } catch (err) {
    container.querySelector(".spinner")?.remove();
    toast(err.message, "error");
  }
}

function detailField(label, value) {
  const wrap = el("div", { className: "profile-field" });
  wrap.appendChild(el("div", { className: "profile-field-label" }, label));
  if (value instanceof HTMLElement) wrap.appendChild(value);
  else wrap.appendChild(el("div", { className: "profile-field-value" }, value || "\u2014"));
  return wrap;
}

function formatDate(iso) {
  if (!iso) return null;
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  } catch { return iso; }
}

