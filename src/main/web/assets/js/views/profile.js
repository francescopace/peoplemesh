import { el, spinner, toast, toastForPromise, emptyState } from "../ui.js";
import { Auth } from "../auth.js";
import { COUNTRIES as COUNTRY_OPTIONS } from "../utils/countries.js";
import { buildFieldMap, buildPartialProfile } from "../utils/profile-import.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import {
  applyImportedProfile,
  getMyConsents,
  getMyProfile,
  getMySkills,
  importMyCv,
  saveMySkills,
  updateMyProfile,
} from "../services/profile-service.js";
import { renderProfileSkillsSection } from "../components/profile-skills-editor.js";
import {
  arr,
  fieldRow,
  labeledField,
  provBadge,
  tagGroup,
} from "../components/profile-fields.js";

const PROFILE_WEIGHTS = {
  identity: 10,
  roles: 15,
  skills: 15,
  industries: 10,
  personal: 10,
  interests: 10,
  geography: 10,
  consent: 10,
};

function computeProfileScore(profile) {
  if (!profile) return 0;
  let score = 0;
  if (profile.identity?.display_name) score += PROFILE_WEIGHTS.identity;
  if (profile.professional?.roles?.length) score += PROFILE_WEIGHTS.roles;
  if (profile.professional?.skills_technical?.length) score += PROFILE_WEIGHTS.skills;
  if (profile.professional?.industries?.length) score += PROFILE_WEIGHTS.industries;
  const pers = profile.personal;
  if (pers && (pers.hobbies?.length || pers.sports?.length || pers.causes?.length || pers.personality_tags?.length))
    score += PROFILE_WEIGHTS.personal;
  const int = profile.interests_professional;
  if (int && (int.topics_frequent?.length || int.learning_areas?.length))
    score += PROFILE_WEIGHTS.interests;
  if (profile.geography?.country) score += PROFILE_WEIGHTS.geography;
  if (profile.consent?.explicit) score += PROFILE_WEIGHTS.consent;
  return score;
}

const SENIORITY_OPTIONS = ["JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"];
const WORK_MODE_OPTIONS = ["REMOTE", "HYBRID", "OFFICE", "FLEXIBLE"];
const EMPLOYMENT_OPTIONS = ["EMPLOYED", "FREELANCE", "FOUNDER", "LOOKING", "OPEN_TO_OFFERS"];
const TIMEZONE_OPTIONS = [
  "Europe/Rome", "Europe/Berlin", "Europe/Paris", "Europe/Madrid",
  "Europe/London", "Europe/Amsterdam", "Europe/Brussels", "Europe/Zurich",
  "Europe/Vienna", "Europe/Warsaw", "Europe/Prague", "Europe/Budapest",
  "Europe/Bucharest", "Europe/Athens", "Europe/Helsinki", "Europe/Stockholm",
  "Europe/Oslo", "Europe/Copenhagen", "Europe/Dublin", "Europe/Lisbon",
  "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
  "America/Toronto", "America/Sao_Paulo",
  "Asia/Tokyo", "Asia/Singapore", "Asia/Kolkata", "Asia/Dubai",
  "Australia/Sydney", "Pacific/Auckland",
  "UTC",
];
export async function renderProfile(container) {
  container.dataset.page = "profile";
  container.innerHTML = "";

  const headerEl = el("header", { className: "profile-header" });
  const headerText = el("div", {});
  headerText.appendChild(el("h1", { className: "page-title" }, "My Profile"));
  headerText.appendChild(el("p", { className: "page-subtitle text-secondary" }, "Manage your multi-dimensional presence."));
  headerEl.appendChild(headerText);

  const fileInput = el("input", { type: "file", accept: ".pdf,.docx", style: "display:none" });
  let cvUploadInProgress = false;
  const setCvButtonsBusy = (busy) => {
    container.querySelectorAll("[data-cv-import-btn]").forEach((btn) => {
      btn.disabled = busy;
      btn.querySelector("span:last-child").textContent = busy ? "Parsing CV..." : "Upload CV";
    });
  };
  const openCvPicker = () => { fileInput.value = ""; fileInput.click(); };
  fileInput.onchange = async (e) => {
    if (cvUploadInProgress) return;
    const file = e.target.files[0];
    if (!file) return;
    const fd = new FormData();
    fd.append("file", file);
    cvUploadInProgress = true;
    setCvButtonsBusy(true);
    try {
      const result = await toastForPromise(
        () => importMyCv(fd),
        {
          loadingMessage: "Parsing CV... this may take up to 40 seconds.",
          successMessage: "CV parsed. Review the imported fields below.",
          errorMessage: (err) => err.message,
          minVisibleMs: 1000,
        }
      );
      const current = await getMyProfile().catch(() => null);
      showImportPreviewModal(result.imported, current, result.source, container);
    } catch {
      // Errors are surfaced by toastForPromise.
    }
    finally {
      cvUploadInProgress = false;
      setCvButtonsBusy(false);
      fileInput.value = "";
    }
  };
  container.appendChild(fileInput);

  const IMPORT_PROVIDERS = {
    github: "GitHub",
  };
  const headerActions = el("div", { className: "profile-header-actions" });
  for (const [pid, label] of Object.entries(IMPORT_PROVIDERS)) {
    if (Auth.isProviderConfigured(pid)) {
      headerActions.appendChild(el("button", {
        className: "btn btn-secondary",
        onClick: () => {
          const url = `/api/v1/auth/login/${pid}?intent=profile_import`;
          window.open(url, "pm_import", "width=600,height=700,popup=yes");
        }
      },
        el("i", { className: "fa-brands fa-github", style: "font-size:18px" }),
        el("span", {}, `Import from ${label}`)
      ));
    }
  }
  headerActions.appendChild(el("button", {
    className: "btn btn-secondary",
    dataset: { cvImportBtn: "true" },
    onClick: () => openCvPicker()
  },
    el("span", { className: "material-symbols-outlined icon-18" }, "upload"),
    el("span", {}, "Import from CV")
  ));
  headerEl.appendChild(headerActions);
  container.appendChild(headerEl);

  container.appendChild(spinner());

  let profile;
  try { profile = await getMyProfile(); }
  catch (err) {
    if (err.status === 404) profile = null;
    else { toast(getUserFacingErrorMessage(err, "Could not load profile."), "error"); return; }
  }

  container.querySelector(".spinner")?.remove();

  if (profile) {
    const contentArea = el("div", { className: "profile-content" });
    container.appendChild(contentArea);
    renderProfileView(contentArea, profile);
  } else {
    container.appendChild(emptyState("No profile yet. Use an import button above or create one below."));
  }

  if (container._profileImportAbort) {
    container._profileImportAbort.abort();
  }
  const importAbort = new AbortController();
  container._profileImportAbort = importAbort;

  function onImportMessage(event) {
    if (event.origin !== window.location.origin) return;
    const data = event.data;
    if (!data || data.type !== "import-result") {
      if (data && data.type === "import-error") {
        toast(data.error || "Import failed", "error");
      }
      return;
    }
    const currentProfile = profile || null;
    showImportPreviewModal(data.imported, currentProfile, data.source, container);
  }
  window.addEventListener("message", onImportMessage, { signal: importAbort.signal });
}

function showImportPreviewModal(imported, current, source, container) {
  const FIELD_MAP = buildFieldMap(imported, current, source);
  const selected = new Set();
  FIELD_MAP.forEach((f) => {
    if (f.importedDisplay && f.importedDisplay !== "\u2014") selected.add(f.key);
  });
  const mergeModes = new Map();
  FIELD_MAP.forEach((f) => {
    if (f.canMerge && f.hasConflict) mergeModes.set(f.key, "merge");
  });

  const sourceLabel = formatProvSource(source);

  const overlay = el("div", { className: "modal-overlay import-preview-overlay" });
  const dialog = el("div", {
    className: "modal-dialog import-preview-dialog",
    role: "dialog",
    "aria-modal": "true",
    "aria-label": `Import from ${sourceLabel}`,
  });

  const header = el("div", { className: "modal-header" },
    el("div", { className: "import-preview-title-row" },
      el("span", { className: "material-symbols-outlined import-preview-icon" }, "sync_alt"),
      el("h3", {}, `Import from ${sourceLabel}`),
    ),
    el("button", {
      className: "modal-close btn btn-ghost",
      "aria-label": "Close",
      onClick: () => discardAndClose(),
    }, "\u00d7")
  );
  dialog.appendChild(header);

  const intro = el("div", { className: "import-preview-intro" },
    el("p", {},
      "Select which fields to import. Deselect any field you want to keep as-is."
    ),
    el("p", { className: "text-secondary" },
      "For list fields, we can merge new values with your current profile."
    ),
  );

  const selectAllWrap = el("div", { className: "import-preview-select-all" });
  const selectAllCb = el("input", { type: "checkbox" });
  selectAllCb.checked = selected.size === FIELD_MAP.filter((f) => f.importedDisplay && f.importedDisplay !== "\u2014").length;
  selectAllCb.addEventListener("change", () => {
    FIELD_MAP.forEach((f) => {
      if (!f.importedDisplay || f.importedDisplay === "\u2014") return;
      if (selectAllCb.checked) selected.add(f.key);
      else selected.delete(f.key);
    });
    renderRows();
  });
  selectAllWrap.appendChild(el("label", { className: "import-preview-select-all-label" },
    selectAllCb,
    el("span", {}, "Select all")
  ));
  intro.appendChild(selectAllWrap);

  const body = el("div", { className: "modal-body import-preview-body" });
  body.appendChild(intro);

  const tableWrap = el("div", { className: "import-preview-table-wrap" });
  body.appendChild(tableWrap);

  function renderRows() {
    tableWrap.innerHTML = "";
    let currentSection = "";
    for (const f of FIELD_MAP) {
      if (!f.importedDisplay || f.importedDisplay === "\u2014") continue;

      if (f.section !== currentSection) {
        currentSection = f.section;
        tableWrap.appendChild(el("div", { className: "import-preview-section" }, currentSection));
      }

      const isChecked = selected.has(f.key);
      const hasConflict = f.hasConflict;
      const mergeEnabled = mergeModes.get(f.key) === "merge";

      const row = el("div", {
        className: `import-preview-row${isChecked ? " import-preview-row--selected" : ""}${hasConflict ? " import-preview-row--conflict" : ""}${mergeEnabled ? " import-preview-row--merge" : ""}`,
        onClick: (e) => {
          if (e.target.tagName === "INPUT") return;
          if (e.target instanceof Element && e.target.closest(".import-preview-merge-label")) return;
          cb.checked = !cb.checked;
          cb.dispatchEvent(new Event("change"));
        },
      });

      const cb = el("input", { type: "checkbox", className: "import-preview-cb" });
      cb.checked = isChecked;
      cb.addEventListener("change", () => {
        if (cb.checked) selected.add(f.key);
        else selected.delete(f.key);
        row.classList.toggle("import-preview-row--selected", cb.checked);
        updateFooterCount();
      });

      const info = el("div", { className: "import-preview-field-info" });
      info.appendChild(el("div", { className: "import-preview-field-label" }, f.label));

      const values = el("div", { className: "import-preview-values" });

      if (f.currentDisplay && f.currentDisplay !== "\u2014") {
        const cur = el("div", { className: "import-preview-current" });
        cur.appendChild(el("span", { className: "import-preview-val-label" }, "Current"));
        cur.appendChild(el("span", { className: "import-preview-val" }, f.currentDisplay));
        if (f.currentProv) {
          cur.appendChild(el("span", { className: "import-preview-prov" }, formatProvSource(f.currentProv)));
        }
        values.appendChild(cur);
      }

      const imp = el("div", { className: "import-preview-imported" });
      imp.appendChild(el("span", { className: "import-preview-val-label" }, "New"));
      imp.appendChild(el("span", { className: "import-preview-val import-preview-val--new" }, f.importedDisplay));
      values.appendChild(imp);

      if (f.canMerge && mergeEnabled && f.mergedDisplay && f.mergedDisplay !== "\u2014") {
        const merged = el("div", { className: "import-preview-result" });
        merged.appendChild(el("span", { className: "import-preview-val-label" }, "Result"));
        merged.appendChild(el("span", { className: "import-preview-val" }, f.mergedDisplay));
        values.appendChild(merged);
      }

      info.appendChild(values);

      if (f.canMerge) {
        const mergeLabel = el("label", { className: "import-preview-merge-label" });
        const mergeCb = el("input", { type: "checkbox", className: "import-preview-merge-cb" });
        mergeCb.checked = mergeEnabled;
        mergeCb.addEventListener("change", () => {
          if (mergeCb.checked) mergeModes.set(f.key, "merge");
          else mergeModes.delete(f.key);
          renderRows();
        });
        mergeLabel.appendChild(mergeCb);
        mergeLabel.appendChild(el("span", {}, "Merge with current values"));
        info.appendChild(mergeLabel);
      }

      row.appendChild(cb);
      row.appendChild(info);
      tableWrap.appendChild(row);
    }
  }

  renderRows();
  dialog.appendChild(body);

  const footer = el("div", { className: "modal-footer import-preview-footer" });
  const countSpan = el("span", { className: "import-preview-count text-secondary" });
  const cancelBtn = el("button", {
    className: "btn btn-secondary",
    onClick: () => discardAndClose(),
  }, "Cancel");
  const applyBtn = el("button", { className: "btn btn-primary" },
    el("span", { className: "material-symbols-outlined icon-18" }, "check"),
    el("span", {}, "Import & Save"),
  );

  function updateFooterCount() {
    countSpan.textContent = `${selected.size} field${selected.size !== 1 ? "s" : ""} selected`;
  }
  updateFooterCount();

  applyBtn.addEventListener("click", async () => {
    if (selected.size === 0) {
      toast("No fields selected", "info");
      return;
    }
    applyBtn.disabled = true;
    cancelBtn.disabled = true;
    applyBtn.querySelector("span:last-child").textContent = "Saving...";
    try {
      const partial = buildPartialProfile(imported, current, selected, mergeModes);
      await applyImportedProfile(source, partial);
      toast(`Imported ${selected.size} field${selected.size !== 1 ? "s" : ""} from ${sourceLabel}`, "success");
      overlay.remove();
      container.innerHTML = "";
      await renderProfile(container);
    } catch (err) {
      toast(getUserFacingErrorMessage(err, "Could not import selected fields."), "error");
      applyBtn.disabled = false;
      cancelBtn.disabled = false;
      applyBtn.querySelector("span:last-child").textContent = "Import & Save";
    }
  });

  footer.appendChild(countSpan);
  footer.appendChild(cancelBtn);
  footer.appendChild(applyBtn);
  dialog.appendChild(footer);

  overlay.appendChild(dialog);
  overlay.addEventListener("click", (e) => { if (e.target === overlay) discardAndClose(); });
  document.body.appendChild(overlay);

  function discardAndClose() {
    overlay.remove();
  }
}

/* === Profile view (2-column layout matching mockup) === */

function renderProfileView(container, p) {
  const prov = p.field_provenance || {};
  const prof = p.professional || {};
  const contacts = p.contacts || {
    slack_handle: prof.slack_handle,
    telegram_handle: prof.telegram_handle,
    mobile_phone: prof.mobile_phone,
    linkedin_url: prof.linkedin_url,
  };
  const identity = p.identity || {};
  const geo = p.geography || {};
  const interests = p.interests_professional || {};
  const personal = p.personal || {};

  const canEdit = true;
  const root = () => container.closest("[data-page='profile']") || container.parentElement;

  const grid = el("div", { className: "profile-grid" });

  /* Identity */
  const identitySection = editableSection("person", "Identity", "blue", canEdit, p, root,
    (editing) => {
      const content = [];
      if (identity.photo_url) {
        const avatarRow = el("div", { className: "profile-identity-avatar" });
        if (identity.photo_url) {
          const img = el("img", {
            src: identity.photo_url,
            alt: identity.display_name || "Profile photo",
            className: "profile-avatar-img",
            referrerpolicy: "no-referrer",
          });
          avatarRow.appendChild(img);
          if (prov["identity.photo_url"]) avatarRow.appendChild(provBadge(prov["identity.photo_url"]));
        }
        content.push(avatarRow);
      }
      content.push(fieldRow([
        labeledField("Display Name", identity.display_name, prov["identity.display_name"]),
        labeledField("Email", identity.email, prov["identity.email"]),
      ]));
      content.push(fieldRow([
        labeledField("First Name", identity.first_name, prov["identity.first_name"]),
        labeledField("Last Name", identity.last_name, prov["identity.last_name"]),
      ]));
      content.push(fieldRow([
        labeledField("Company", identity.company, prov["identity.company"]),
        editableLabeledField("Birth Date", "birthDate", identity.birth_date, editing, prov["identity.birth_date"], "date"),
      ]));
      return content;
    },
    (body) => ({
      identity: {
        birth_date: val(body, "birthDate") || undefined,
      },
    })
  );
  identitySection.classList.add("profile-grid-main");
  identitySection.style.gridRow = "1 / 3";
  grid.appendChild(identitySection);

  /* Location (under Identity) */
  const locationSection = editableSection("language", "Location", "blue", canEdit, p, root,
    (editing) => {
      return [fieldRow([
        editableLabeledField("City", "city", geo.city, editing),
        editablePairSelect("Country", "country", COUNTRY_OPTIONS, geo.country, editing),
        editableSelect("Timezone", "timezone", TIMEZONE_OPTIONS, geo.timezone, editing),
      ])];
    },
    (body) => ({
      geography: {
        country: val(body, "country") || undefined,
        city: val(body, "city") || undefined,
        timezone: val(body, "timezone") || undefined,
      },
    })
  );
  locationSection.classList.add("profile-grid-full");
  grid.appendChild(locationSection);

  /* Contacts */
  const contactsSection = editableSection("contact_phone", "Contacts", "purple", canEdit, p, root,
    (editing) => {
      const content = [];
      content.push(fieldRow([
        editing
          ? editableLabeledField("Slack", "slackHandle", contacts.slack_handle, editing, prov["contacts.slack_handle"] || prov["professional.slack_handle"])
          : (contacts.slack_handle
            ? slackField(contacts.slack_handle, prov["contacts.slack_handle"] || prov["professional.slack_handle"])
            : labeledField("Slack", "", prov["contacts.slack_handle"] || prov["professional.slack_handle"])),
        editing
          ? editableLabeledField("Telegram", "telegramHandle", contacts.telegram_handle, editing, prov["contacts.telegram_handle"] || prov["professional.telegram_handle"])
          : (contacts.telegram_handle
            ? telegramField(contacts.telegram_handle, prov["contacts.telegram_handle"] || prov["professional.telegram_handle"])
            : labeledField("Telegram", "", prov["contacts.telegram_handle"] || prov["professional.telegram_handle"])),
        editing
          ? editableLabeledField("Mobile", "mobilePhone", contacts.mobile_phone, editing, prov["contacts.mobile_phone"] || prov["professional.mobile_phone"], "tel")
          : phoneField(contacts.mobile_phone, prov["contacts.mobile_phone"] || prov["professional.mobile_phone"]),
        editing
          ? editableLabeledField("LinkedIn", "linkedinUrl", contacts.linkedin_url, editing, prov["contacts.linkedin_url"] || prov["professional.linkedin_url"], "url")
          : linkedinField(contacts.linkedin_url, prov["contacts.linkedin_url"] || prov["professional.linkedin_url"]),
      ]));
      return content;
    },
    (body) => ({
      contacts: {
        slack_handle: val(body, "slackHandle") || undefined,
        telegram_handle: val(body, "telegramHandle") || undefined,
        mobile_phone: val(body, "mobilePhone") || undefined,
        linkedin_url: val(body, "linkedinUrl") || undefined,
      },
    })
  );
  contactsSection.classList.add("profile-grid-full");
  grid.appendChild(contactsSection);

  /* === Sidebar cards (grid column 2) === */

  /* Profile Completion */
  const profileScore = computeProfileScore(p);
  const completionCard = el("div", { className: "profile-card profile-grid-sidebar profile-grid-row-1" });
  const completionHeader = el("div", { className: "profile-card-header" });
  completionHeader.appendChild(el("h3", { className: "profile-card-title profile-card-title-sm" },
    el("span", { className: "material-symbols-outlined profile-icon profile-icon--blue icon-20" }, "donut_large"),
    "Profile Completion"
  ));
  completionCard.appendChild(completionHeader);
  const completionBody = el("div", { className: "profile-card-body" });
  const barOuter = el("div", { className: "dash-progress-track" });
  const barInner = el("div", { className: "dash-progress-fill" });
  barInner.style.width = `${profileScore}%`;
  barOuter.appendChild(barInner);
  completionBody.appendChild(barOuter);
  const completionInfo = el("div", { className: "dash-profile-info" });
  completionInfo.appendChild(el("span", { className: "dash-profile-score" }, `${profileScore}% complete`));
  if (profileScore < 100) {
    const missing = [];
    if (!p.professional?.roles?.length) missing.push("roles");
    if (!p.professional?.skills_technical?.length) missing.push("skills");
    if (!p.personal?.hobbies?.length && !p.personal?.sports?.length) missing.push("interests");
    if (!p.geography?.country) missing.push("location");
    if (missing.length) {
      const hint = el("span", { className: "text-sm text-secondary d-block" });
      hint.textContent = `Add ${missing.slice(0, 3).join(", ")} to improve matching`;
      completionInfo.appendChild(hint);
    }
  } else {
    completionInfo.appendChild(el("span", { className: "text-sm text-success" }, "Profile complete"));
  }
  completionBody.appendChild(completionInfo);
  completionCard.appendChild(completionBody);
  grid.appendChild(completionCard);

  /* Privacy & Visibility */
  const privCard = el("a", {
    href: "#/privacy",
    className: "profile-privacy-card profile-grid-sidebar profile-grid-row-2 link-reset cursor-pointer",
  });
  const privIcon = el("div", { className: "profile-privacy-icon-wrap" });
  privIcon.appendChild(el("span", { className: "material-symbols-outlined" }, "shield_with_heart"));
  privCard.appendChild(privIcon);
  privCard.appendChild(el("h3", { className: "profile-privacy-title" }, "Privacy & Visibility"));
  const privStatus = el("p", { className: "profile-privacy-status text-secondary" }, "Loading...");
  privCard.appendChild(privStatus);
  grid.appendChild(privCard);

  getMyConsents().then((data) => {
    const active = data.active || [];
    const total = (data.scopes || []).length;
    privStatus.textContent = `${active.length} of ${total} consents active.`;
    privCard.querySelector(".profile-privacy-icon-wrap").classList.toggle("profile-privacy-icon-wrap--restricted", active.length < total);
  }).catch(() => {
    privStatus.textContent = "Could not load privacy status.";
  });

  /* === Full-width sections (span all columns) === */

  /* Professional */
  const profSection = editableSection("business_center", "Professional", "purple", canEdit, p, root,
    (editing) => {
      const roleVal = Array.isArray(prof.roles) ? (prof.roles[0] || "") : (prof.roles || "");
      const content = [];
      content.push(fieldRow([
        editableLabeledField("Current Role", "roles", roleVal, editing, prov["professional.roles"]),
        editableSelect("Seniority", "seniority", SENIORITY_OPTIONS, prof.seniority, editing, prov["professional.seniority"]),
      ]));
      if (prof.skills_technical?.length || editing)
        content.push(editableTagGroup("Technical Skills", "skills", prof.skills_technical, "blue", editing, prov["professional.skills_technical"]));
      if (prof.skills_soft?.length || editing)
        content.push(editableTagGroup("Soft Skills", "softSkills", prof.skills_soft, "purple", editing, prov["professional.skills_soft"]));
      if (prof.tools_and_tech?.length || editing)
        content.push(editableTagGroup("Tools & Tech", "toolsAndTech", prof.tools_and_tech, "blue", editing, prov["professional.tools_and_tech"]));
      content.push(fieldRow([
        editableSelect("Work Mode", "workMode", WORK_MODE_OPTIONS, prof.work_mode_preference, editing, prov["professional.work_mode_preference"]),
        editableSelect("Employment", "employmentType", EMPLOYMENT_OPTIONS, prof.employment_type, editing, prov["professional.employment_type"]),
        editing
          ? editableLabeledField("Languages Spoken", "languagesSpoken", arr(prof.languages_spoken), editing, prov["professional.languages_spoken"])
          : labeledField("Languages", arr(prof.languages_spoken), prov["professional.languages_spoken"]),
      ]));
      if (prof.industries?.length || editing)
        content.push(editableTagGroup("Industries", "industries", prof.industries, "slate", editing, prov["professional.industries"]));
      return content;
    },
    (body) => ({
      professional: {
        roles: val(body, "roles") ? [val(body, "roles").trim()] : [],
        seniority: val(body, "seniority") || undefined,
        skills_technical: csvList(val(body, "skills")),
        skills_soft: csvList(val(body, "softSkills")),
        tools_and_tech: csvList(val(body, "toolsAndTech")),
        languages_spoken: csvList(val(body, "languagesSpoken")),
        industries: csvList(val(body, "industries")),
        work_mode_preference: val(body, "workMode") || undefined,
        employment_type: val(body, "employmentType") || undefined,
      },
    })
  );
  profSection.classList.add("profile-grid-full");
  grid.appendChild(profSection);

  /* Skills Assessment */
  {
    const skillsCard = el("div", { className: "profile-card" });
    const skillsHeader = el("div", { className: "profile-card-header" });
    skillsHeader.appendChild(el("h2", { className: "profile-card-title" },
      el("span", { className: "material-symbols-outlined profile-icon profile-icon--blue" }, "psychology"),
      "Skills Assessment"
    ));
    skillsCard.appendChild(skillsHeader);
    const skillsBody = el("div", { className: "profile-card-body" });
    skillsCard.appendChild(skillsBody);
    skillsCard.classList.add("profile-grid-full");
    grid.appendChild(skillsCard);
    renderProfileSkillsSection({
      container: skillsBody,
      canEdit,
      getRoot: root,
      fetchSkills: getMySkills,
      saveSkills: saveMySkills,
      rerenderProfile: renderProfile,
    });
  }

  /* Interests & Personal */
  const hasPersonalContent = personal.hobbies?.length || personal.sports?.length ||
    personal.causes?.length || interests.topics_frequent?.length ||
    interests.learning_areas?.length ||
    personal.music_genres?.length || personal.book_genres?.length ||
    personal.personality_tags?.length || personal.education?.length;

  if (hasPersonalContent || canEdit) {
    const interestsSection = editableSection("favorite", "Interests & Personal", "emerald", canEdit, p, root,
      (editing) => {
        const content = [];
        if (personal.hobbies?.length || editing)
          content.push(editableTagGroup("Hobbies", "hobbies", personal.hobbies, "emerald", editing));
        if (personal.sports?.length || editing)
          content.push(editableTagGroup("Sports", "sports", personal.sports, "emerald", editing));
        if (personal.causes?.length || editing)
          content.push(editableTagGroup("Causes", "causes", personal.causes, "purple", editing));
        if (interests.topics_frequent?.length || editing)
          content.push(editableTagGroup("Topics", "topics", interests.topics_frequent, "blue", editing, prov["interests_professional.topics_frequent"]));
        if (interests.learning_areas?.length || editing)
          content.push(editableTagGroup("Learning Areas", "learningAreas", interests.learning_areas, "slate", editing, prov["interests_professional.learning_areas"]));
        if (interests.project_types?.length || editing)
          content.push(editableTagGroup("Project Types", "projectTypes", interests.project_types, "blue", editing));
        if (personal.music_genres?.length || editing)
          content.push(editableTagGroup("Music", "musicGenres", personal.music_genres, "emerald", editing));
        if (personal.book_genres?.length || editing)
          content.push(editableTagGroup("Books", "bookGenres", personal.book_genres, "purple", editing));
        if (personal.personality_tags?.length || editing)
          content.push(editableTagGroup("Personality", "personalityTags", personal.personality_tags, "slate", editing));
        if (personal.education?.length || editing)
          content.push(editableTagGroup("Education", "education", personal.education, "blue", editing));
        return content;
      },
      (body) => ({
        interests_professional: {
          topics_frequent: csvList(val(body, "topics")),
          learning_areas: csvList(val(body, "learningAreas")),
          project_types: csvList(val(body, "projectTypes")),
        },
        personal: {
          hobbies: csvList(val(body, "hobbies")),
          sports: csvList(val(body, "sports")),
          education: csvList(val(body, "education")),
          causes: csvList(val(body, "causes")),
          personality_tags: csvList(val(body, "personalityTags")),
          music_genres: csvList(val(body, "musicGenres")),
          book_genres: csvList(val(body, "bookGenres")),
        },
      })
    );
    interestsSection.classList.add("profile-grid-full");
    grid.appendChild(interestsSection);
  }

  container.appendChild(grid);
}

/* === Editable section: toggles between view and inline-edit === */

function editableSection(icon, title, color, canEdit, profileData, getRoot, renderContent, collectPayload) {
  const card = el("div", { className: "profile-card" });
  let editing = false;

  const header = el("div", { className: "profile-card-header" });
  const titleEl = el("h2", { className: "profile-card-title" },
    el("span", { className: `material-symbols-outlined profile-icon profile-icon--${color}` }, icon),
    title
  );
  header.appendChild(titleEl);

  const editBtn = el("button", { className: "profile-edit-link" }, "Edit");
  if (canEdit) header.appendChild(editBtn);
  card.appendChild(header);

  const body = el("div", { className: "profile-card-body" });
  card.appendChild(body);

  function render() {
    body.innerHTML = "";
    body.classList.toggle("profile-card-body--editing", editing);
    card.classList.toggle("profile-card--editing", editing);
    const content = renderContent(editing);
    content.forEach((c) => body.appendChild(c));

    if (editing) {
      const actions = el("div", { className: "profile-inline-actions" });
      const saveBtn = el("button", { className: "btn btn-primary btn-sm", type: "button" },
        el("span", { className: "material-symbols-outlined icon-16" }, "save"),
        "Save"
      );
      const cancelBtn = el("button", { className: "btn btn-secondary btn-sm", type: "button" }, "Cancel");
      cancelBtn.addEventListener("click", () => { editing = false; render(); });
      saveBtn.addEventListener("click", async () => {
        saveBtn.disabled = true;
        try {
          const partial = collectPayload(body);
          await updateMyProfile({ ...profileData, ...partial });
          toast("Profile saved", "success");
          const r = getRoot();
          r.innerHTML = "";
          await renderProfile(r);
        } catch (err) {
          toast(getUserFacingErrorMessage(err, "Could not save profile."), "error");
          saveBtn.disabled = false;
        }
      });
      actions.appendChild(saveBtn);
      actions.appendChild(cancelBtn);
      body.appendChild(actions);
      editBtn.textContent = "Cancel";
    } else {
      editBtn.textContent = "Edit";
    }
  }

  editBtn.addEventListener("click", () => {
    editing = !editing;
    render();
  });

  render();
  return card;
}

/* === Inline edit field builders === */

function editableLabeledField(label, name, value, editing, provSrc, type = "text") {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  if (editing) {
    wrap.appendChild(el("input", {
      className: "profile-editable-value", type, name,
      dataset: { field: name },
      value: value || "",
      placeholder: "\u2014",
    }));
  } else {
    wrap.appendChild(el("div", { className: "profile-field-value" }, value || "\u2014"));
  }
  return wrap;
}

function editableSelect(label, name, options, current, editing, provSrc) {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  if (editing) {
    const select = el("select", {
      className: "profile-editable-value profile-editable-value--select", name,
      dataset: { field: name },
    });
    select.appendChild(el("option", { value: "" }, "Select..."));
    options.forEach((opt) => {
      const o = el("option", { value: opt }, opt);
      if (opt === current) o.selected = true;
      select.appendChild(o);
    });
    wrap.appendChild(select);
  } else {
    wrap.appendChild(el("div", { className: "profile-field-value" }, current || "\u2014"));
  }
  return wrap;
}

function editablePairSelect(label, name, pairs, current, editing, provSrc) {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  if (editing) {
    const select = el("select", {
      className: "profile-editable-value profile-editable-value--select", name,
      dataset: { field: name },
    });
    select.appendChild(el("option", { value: "" }, "Select..."));
    pairs.forEach(([v, lbl]) => {
      const o = el("option", { value: v }, `${lbl} (${v})`);
      if (v === current) o.selected = true;
      select.appendChild(o);
    });
    wrap.appendChild(select);
  } else {
    const display = pairs.find(([v]) => v === current);
    wrap.appendChild(el("div", { className: "profile-field-value" }, display ? `${display[1]} (${current})` : (current || "\u2014")));
  }
  return wrap;
}

function editableTagGroup(label, name, items, color, editing, provSrc) {
  const wrap = el("div", { className: "profile-tag-group" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  if (editing) {
    wrap.appendChild(el("input", {
      className: "profile-editable-value", type: "text", name,
      dataset: { field: name },
      value: Array.isArray(items) ? items.join(", ") : (items || ""),
      placeholder: "comma-separated values",
    }));
  } else {
    const tags = el("div", { className: "profile-tags" });
    (Array.isArray(items) ? items : []).forEach((item) => {
      tags.appendChild(el("span", { className: `profile-tag profile-tag--${color}` }, item));
    });
    wrap.appendChild(tags);
  }
  return wrap;
}

/* === Helpers: display builders === */

function formatProvSource(src) {
  if (!src) return "Unknown";
  if (src.includes("cv_docling_llm")) return "CV";
  if (src.includes("github")) return "GitHub";
  if (src.includes("manual")) return "Manual";
  return src;
}

function slackField(handle, provSrc) {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, "Slack");
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const cleanHandle = handle.replace(/^@/, "");
  const displayHandle = cleanHandle ? `@${cleanHandle}` : handle;
  const link = el("a", {
    href: `slack://user?team=&id=${cleanHandle}`,
    target: "_blank",
    rel: "noopener",
    style: "display:flex;align-items:center;gap:0.4rem;text-decoration:none;color:inherit",
  });
  link.appendChild(el("i", { className: "fa-brands fa-slack", style: "font-size:16px;color:#611f69" }));
  link.appendChild(document.createTextNode(displayHandle));
  const valueWrap = el("div", { className: "profile-field-value" });
  valueWrap.appendChild(link);
  wrap.appendChild(valueWrap);
  return wrap;
}

function telegramField(handle, provSrc) {
  const cleanHandle = handle.replace(/^@/, "");
  const displayHandle = cleanHandle ? `@${cleanHandle}` : handle;
  return renderContactLinkField("Telegram", displayHandle, `https://t.me/${cleanHandle}`, provSrc);
}

function phoneField(phone, provSrc) {
  if (!phone) return labeledField("Mobile", "", provSrc);
  const normalizedPhone = phone.replace(/\s+/g, "");
  return renderContactLinkField("Mobile", phone, `tel:${normalizedPhone}`, provSrc);
}

function linkedinField(linkedinUrl, provSrc) {
  if (!linkedinUrl) return labeledField("LinkedIn", "", provSrc);
  const normalizedUrl = normalizeLinkedinUrl(linkedinUrl);
  return renderContactLinkField("LinkedIn", normalizedUrl, normalizedUrl, provSrc);
}

function normalizeLinkedinUrl(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  if (/^https?:\/\//i.test(raw)) return raw;
  return `https://${raw}`;
}

function renderContactLinkField(label, text, href, provSrc) {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const linkAttrs = {
    href,
    style: "display:flex;align-items:center;gap:0.4rem;text-decoration:none;color:inherit",
  };
  if (!href.startsWith("tel:")) {
    linkAttrs.target = "_blank";
    linkAttrs.rel = "noopener";
  }
  const link = el("a", linkAttrs, text);
  const valueWrap = el("div", { className: "profile-field-value" });
  valueWrap.appendChild(link);
  wrap.appendChild(valueWrap);
  return wrap;
}

function val(root, field) {
  const input = root.querySelector(`[data-field="${field}"]`);
  return (input?.value || "").trim();
}
function csvList(s) { return s ? s.split(",").map((x) => x.trim()).filter(Boolean) : []; }

export { buildFieldMap, buildPartialProfile };
