import { el, spinner, toast } from "../ui.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";

export async function renderProfileSkillsSection({
  container,
  canEdit,
  getRoot,
  fetchSkills,
  saveSkills,
  rerenderProfile,
}) {
  container.innerHTML = "";
  container.appendChild(spinner());
  container.querySelector(".spinner")?.remove();

  const controlRow = el("div", { className: "flex gap-3 profile-skills-control-row" });
  container.appendChild(controlRow);

  const skillsArea = el("div", {});
  container.appendChild(skillsArea);

  let editing = false;
  let hasLoadedAssessments = false;
  let currentAssessments = [];
  const levelScale = { "0": "None", "1": "Aware", "2": "Beginner", "3": "Practitioner", "4": "Advanced", "5": "Expert" };

  function renderAssessments() {
    skillsArea.innerHTML = "";
    if (editing) {
      renderSkillsEdit(skillsArea, currentAssessments, levelScale, getRoot, saveSkills, rerenderProfile);
    } else {
      renderSkillsView(skillsArea, currentAssessments);
    }
  }

  if (canEdit) {
    const editBtn = el("button", { className: "profile-edit-link ml-auto" }, "Edit Skills");
    controlRow.appendChild(editBtn);
    editBtn.addEventListener("click", () => {
      editing = !editing;
      editBtn.textContent = editing ? "Cancel" : "Edit Skills";
      renderAssessments();
    });
  }

  async function loadAssessments(forceReload = false) {
    if (hasLoadedAssessments && !forceReload) {
      renderAssessments();
      return;
    }

    skillsArea.innerHTML = "";
    skillsArea.appendChild(spinner());

    try {
      const data = await fetchSkills();
      currentAssessments = data || [];
      hasLoadedAssessments = true;
    } catch {
      skillsArea.querySelector(".spinner")?.remove();
      skillsArea.appendChild(el("p", { className: "text-secondary" }, "Could not load skills."));
      return;
    }

    renderAssessments();
  }

  loadAssessments();
}

function renderSkillsView(container, assessments) {
  const confirmed = assessments.filter((a) => a.source && a.source !== "suggestion" && a.match_type == null);
  const suggestions = assessments.filter((a) => a.match_type != null);

  if (!confirmed.length && !suggestions.length) {
    container.appendChild(el("p", { className: "text-secondary" }, "No skill assessments yet. Edit to add your skills."));
    return;
  }

  if (confirmed.length) {
    const grouped = {};
    for (const a of confirmed) {
      const cat = a.category || "Other";
      if (!grouped[cat]) grouped[cat] = [];
      grouped[cat].push(a);
    }

    for (const [category, items] of Object.entries(grouped)) {
      const catWrap = el("div", { className: "profile-tag-group" });
      catWrap.appendChild(el("div", { className: "profile-field-label" }, category));
      const tags = el("div", { className: "profile-tags" });
      for (const s of items) {
        const tag = el("span", { className: "profile-tag profile-tag--blue", style: "display:inline-flex;align-items:center;gap:0.3rem" });
        tag.appendChild(document.createTextNode(s.skill_name));
        tag.appendChild(skillLevelBar(s.level));
        if (s.interest) {
          tag.appendChild(el("span", {
            className: "skill-interest-dot",
            title: "Interested in growing",
            style: "color:#f59e0b",
          }, "\u2605"));
        }
        tags.appendChild(tag);
      }
      catWrap.appendChild(tags);
      container.appendChild(catWrap);
    }
  }

  if (suggestions.length) {
    const sugWrap = el("div", { className: "profile-tag-group", style: "margin-top:var(--space-4)" });
    sugWrap.appendChild(el("div", { className: "profile-field-label" }, "Suggested Skills (from reconciliation)"));
    const tags = el("div", { className: "profile-tags" });
    for (const s of suggestions) {
      const tag = el("span", { className: "profile-tag profile-tag--slate", style: "opacity:0.7" });
      tag.appendChild(document.createTextNode(s.skill_name));
      tag.appendChild(el("span", { style: "font-size:0.65rem;margin-left:0.3rem;opacity:0.7" },
        `${s.match_type} ${Math.round(s.confidence * 100)}%`
      ));
      tags.appendChild(tag);
    }
    sugWrap.appendChild(tags);
    container.appendChild(sugWrap);
  }
}

function renderSkillsEdit(container, assessments, levelScale, getRoot, saveSkills, rerenderProfile) {
  const confirmed = assessments.filter((a) => a.match_type == null && a.source !== "suggestion");
  const suggestions = assessments.filter((a) => a.match_type != null);

  const editList = [...confirmed];
  const listArea = el("div", { className: "stack-3" });

  function renderEditRows() {
    listArea.innerHTML = "";
    if (!editList.length) {
      listArea.appendChild(el("p", { className: "text-secondary" }, "No skills yet."));
    }
    for (let i = 0; i < editList.length; i++) {
      const a = editList[i];
      const row = el("div", {
        className: "flex gap-3",
        style: "align-items:center;padding:var(--space-2) 0;border-bottom:1px solid var(--glass-border)",
      });
      row.appendChild(el("span", { style: "flex:1;font-size:0.85rem;font-weight:500" }, a.skill_name || a.skill_id));

      const levelSel = el("select", {
        className: "form-select",
        style: "width:auto;min-width:120px",
        dataset: { idx: String(i), role: "level" },
      });
      for (const [lvl, label] of Object.entries(levelScale)) {
        const opt = el("option", { value: lvl }, `${lvl} - ${label}`);
        if (parseInt(lvl) === a.level) opt.selected = true;
        levelSel.appendChild(opt);
      }
      levelSel.addEventListener("change", () => { editList[i] = { ...editList[i], level: parseInt(levelSel.value) }; });
      row.appendChild(levelSel);

      const interestLabel = el("label", {
        style: "display:flex;align-items:center;gap:0.3rem;font-size:0.8rem;cursor:pointer",
      });
      const interestCb = el("input", { type: "checkbox" });
      if (a.interest) interestCb.checked = true;
      interestCb.addEventListener("change", () => { editList[i] = { ...editList[i], interest: interestCb.checked }; });
      interestLabel.appendChild(interestCb);
      interestLabel.appendChild(el("span", {}, "\u2605 Interest"));
      row.appendChild(interestLabel);

      row.appendChild(el("button", {
        className: "btn btn-ghost btn-sm",
        style: "color:var(--color-red-600)",
        onClick: () => { editList.splice(i, 1); renderEditRows(); },
      }, "\u00d7"));

      listArea.appendChild(row);
    }
  }

  renderEditRows();
  container.appendChild(listArea);

  if (suggestions.length) {
    const sugHeader = el("div", {
      className: "profile-field-label",
      style: "margin-top:var(--space-4);margin-bottom:var(--space-2)",
    }, "Reconciliation Suggestions");
    container.appendChild(sugHeader);
    const sugArea = el("div", { className: "profile-tags" });
    for (const s of suggestions) {
      const tag = el("button", {
        className: "profile-tag profile-tag--slate",
        style: "cursor:pointer;opacity:0.8;border:none;background:rgba(148,163,184,0.12)",
        onClick: () => {
          if (editList.find((e) => e.skill_id === s.skill_id)) {
            toast("Already added", "info");
            return;
          }
          editList.push({ skill_id: s.skill_id, skill_name: s.skill_name, category: s.category, level: 1, interest: false });
          tag.remove();
          renderEditRows();
          toast(`Added ${s.skill_name}`, "success");
        },
      });
      tag.appendChild(document.createTextNode(`+ ${s.skill_name}`));
      tag.appendChild(el("span", { style: "font-size:0.65rem;margin-left:0.3rem;opacity:0.6" },
        `${Math.round(s.confidence * 100)}%`));
      sugArea.appendChild(tag);
    }
    container.appendChild(sugArea);
  }

  const actions = el("div", { className: "profile-inline-actions", style: "margin-top:var(--space-4)" });
  const saveBtn = el("button", { className: "btn btn-primary btn-sm" },
    el("span", { className: "material-symbols-outlined icon-16" }, "save"),
    "Save Skills"
  );
  saveBtn.addEventListener("click", async () => {
    saveBtn.disabled = true;
    try {
      const payload = editList.map((a) => ({
        skill_id: a.skill_id,
        level: a.level,
        interest: a.interest,
      }));
      await saveSkills(payload);
      toast("Skills saved", "success");
      const r = getRoot();
      r.innerHTML = "";
      await rerenderProfile(r);
    } catch (err) {
      toast(getUserFacingErrorMessage(err, "Could not save skills."), "error");
      saveBtn.disabled = false;
    }
  });
  actions.appendChild(saveBtn);
  container.appendChild(actions);
}

function skillLevelBar(level, max = 5) {
  const bar = el("span", { className: "skill-level-bar", title: `Level ${level}/${max}` });
  for (let i = 1; i <= max; i++) {
    bar.appendChild(el("span", {
      className: `skill-level-seg${i <= level ? " skill-level-seg--filled" : ""}`,
    }));
  }
  return bar;
}
