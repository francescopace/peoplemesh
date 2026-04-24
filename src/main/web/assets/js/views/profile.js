import { el, spinner, toast, toastForPromise, emptyState } from "../ui.js";
import { Auth } from "../auth.js";
import { COUNTRIES as COUNTRY_OPTIONS } from "../utils/countries.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import { computeProfileScore, listProfileCompletionHints } from "../utils/profile-completion.js";
import { buildAuthLoginUrl } from "../services/auth-service.js";
import { showProfileImportPreviewModal } from "../components/profile-import-preview-modal.js";
import {
  applyImportedProfile,
  getMyConsents,
  getMyProfileInFlight,
  importMyCv,
  updateMyProfile,
} from "../services/profile-service.js";
import { suggestSkills } from "../services/skills-service.js";
import {
  arr,
  fieldRow,
  labeledField,
  provBadge,
  tagGroup,
} from "../components/profile-fields.js";

const SENIORITY_OPTIONS = ["JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"];
const WORK_MODE_OPTIONS = ["REMOTE", "HYBRID", "OFFICE", "FLEXIBLE"];
const EMPLOYMENT_OPTIONS = ["EMPLOYED", "FREELANCE", "FOUNDER", "LOOKING", "OPEN_TO_OFFERS"];
const SKILL_AUTOCOMPLETE_FIELDS = new Set(["skills", "softSkills", "toolsAndTech"]);
const INLINE_SKILL_FIELD_MAP = Object.freeze({
  skills: "skills_technical",
  softSkills: "skills_soft",
  toolsAndTech: "tools_and_tech",
});
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

export function buildCenteredPopupFeatures(width = 600, height = 700, windowLike = window) {
  const dualScreenLeft = Number.isFinite(windowLike.screenX) ? windowLike.screenX : (windowLike.screenLeft || 0);
  const dualScreenTop = Number.isFinite(windowLike.screenY) ? windowLike.screenY : (windowLike.screenTop || 0);
  const viewportWidth = windowLike.innerWidth
    || windowLike.document?.documentElement?.clientWidth
    || windowLike.screen?.width
    || width;
  const viewportHeight = windowLike.innerHeight
    || windowLike.document?.documentElement?.clientHeight
    || windowLike.screen?.height
    || height;
  const popupWidth = Math.max(1, Math.min(width, Math.floor(viewportWidth * 0.9) || width));
  const popupHeight = Math.max(1, Math.min(height, Math.floor(viewportHeight * 0.9) || height));
  const left = Math.max(0, Math.round(dualScreenLeft + (viewportWidth - popupWidth) / 2));
  const top = Math.max(0, Math.round(dualScreenTop + (viewportHeight - popupHeight) / 2));
  return `popup=yes,width=${popupWidth},height=${popupHeight},left=${left},top=${top}`;
}

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
          errorMessage: (err) => getUserFacingErrorMessage(err, "Could not parse CV."),
          minVisibleMs: 1000,
        }
      );
      const current = await getMyProfileInFlight().catch(() => null);
      showProfileImportPreviewModal({
        imported: result.imported,
        current,
        source: result.source,
        onApply: applyImportedProfile,
        onApplied: async () => {
          container.innerHTML = "";
          await renderProfile(container);
        },
      });
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
          const url = buildAuthLoginUrl(pid, { intent: "profile_import" });
          const popupFeatures = buildCenteredPopupFeatures(600, 700);
          const popup = window.open(url, "pm_import", popupFeatures);
          if (popup) container._profileImportWindow = popup;
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
  try { profile = await getMyProfileInFlight(); }
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
    const expectedSource = container._profileImportWindow;
    if (expectedSource && event.source && event.source !== expectedSource) return;
    const data = event.data;
    if (!data || data.type !== "import-result") {
      if (data && data.type === "import-error") {
        toast(data.error || "Import failed", "error");
      }
      return;
    }
    container._profileImportWindow = null;
    const currentProfile = profile || null;
    showProfileImportPreviewModal({
      imported: data.imported,
      current: currentProfile,
      source: data.source,
      onApply: applyImportedProfile,
      onApplied: async () => {
        container.innerHTML = "";
        await renderProfile(container);
      },
    });
  }
  window.addEventListener("message", onImportMessage, { signal: importAbort.signal });
}

/* === Profile view (2-column layout matching mockup) === */

function renderProfileView(container, p) {
  const prov = p.field_provenance || {};
  const prof = p.professional || {};
  p.professional = prof;
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
  const saveProfessionalTagList = async (field, values) => {
    const nextValues = normalizeTagList(values);
    const updatedProfessional = {
      ...prof,
      [field]: nextValues,
    };
    const updatedProfile = {
      ...p,
      professional: updatedProfessional,
    };
    await updateMyProfile(updatedProfile);
    Object.assign(prof, updatedProfessional);
    p.professional = prof;
  };

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
    const missing = listProfileCompletionHints(p);
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
      content.push(editableTagGroup(
        "Technical Skills",
        "skills",
        prof.skills_technical,
        "blue",
        editing,
        prov["professional.skills_technical"],
        editing
          ? {}
          : {
              quickEdit: {
                placeholder: "Add a technical skill",
                onChange: (nextItems) => saveProfessionalTagList(INLINE_SKILL_FIELD_MAP.skills, nextItems),
              },
            }
      ));
      content.push(editableTagGroup(
        "Soft Skills",
        "softSkills",
        prof.skills_soft,
        "purple",
        editing,
        prov["professional.skills_soft"],
        editing
          ? {}
          : {
              quickEdit: {
                placeholder: "Add a soft skill",
                onChange: (nextItems) => saveProfessionalTagList(INLINE_SKILL_FIELD_MAP.softSkills, nextItems),
              },
            }
      ));
      content.push(editableTagGroup(
        "Tools & Tech",
        "toolsAndTech",
        prof.tools_and_tech,
        "blue",
        editing,
        prov["professional.tools_and_tech"],
        editing
          ? {}
          : {
              quickEdit: {
                placeholder: "Add a tool or technology",
                onChange: (nextItems) => saveProfessionalTagList(INLINE_SKILL_FIELD_MAP.toolsAndTech, nextItems),
              },
            }
      ));
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

  /* Interests & Personal */
  const hasPersonalContent = personal.hobbies?.length || personal.sports?.length ||
    personal.causes?.length || interests.learning_areas?.length ||
    interests.project_types?.length ||
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

function editableTagGroup(label, name, items, color, editing, provSrc, options = {}) {
  const wrap = el("div", { className: "profile-tag-group" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const normalizedItems = normalizeTagList(Array.isArray(items) ? items : []);

  if (editing) {
    const input = el("input", {
      className: "profile-editable-value", type: "text", name,
      dataset: { field: name },
      value: normalizedItems.join(", "),
      placeholder: "comma-separated values",
    });
    wrap.appendChild(input);
    if (SKILL_AUTOCOMPLETE_FIELDS.has(name)) {
      wireSkillAutocomplete(input);
    }
  } else {
    const quickEdit = options.quickEdit && typeof options.quickEdit.onChange === "function"
      ? options.quickEdit
      : null;
    const tags = el("div", { className: "profile-tags" });
    wrap.appendChild(tags);

    if (!quickEdit) {
      normalizedItems.forEach((item) => {
        tags.appendChild(el("span", { className: `profile-tag profile-tag--${color}` }, item));
      });
      return wrap;
    }

    let busy = false;
    let currentItems = [...normalizedItems];
    const addTag = el("div", { className: `profile-tag profile-tag--add profile-tag--${color}` });
    const addInput = el("input", {
      className: "profile-tag-add-input",
      type: "text",
      placeholder: quickEdit.placeholder || "Add a skill",
      "aria-label": `Add ${label}`,
    });
    const addBtn = el("button", {
      className: "profile-tag-add-btn",
      type: "button",
      "aria-label": `Confirm add ${label}`,
      disabled: true,
    }, "+");
    addTag.appendChild(addInput);
    addTag.appendChild(addBtn);

    const updateAddButtonState = () => {
      addBtn.disabled = busy || !addInput.value.trim();
    };

    const setBusy = (isBusy) => {
      busy = isBusy;
      wrap.classList.toggle("profile-tag-group--saving", busy);
      addInput.disabled = busy;
      updateAddButtonState();
      tags.querySelectorAll(".profile-tag-remove").forEach((btn) => {
        btn.disabled = busy;
      });
    };

    const renderTags = () => {
      tags.innerHTML = "";
      currentItems.forEach((item) => {
        const tag = el("span", { className: `profile-tag profile-tag--${color} profile-tag--removable` }, item);
        tag.appendChild(el("button", {
          className: "profile-tag-remove",
          type: "button",
          "aria-label": `Remove ${item}`,
          onClick: async (event) => {
            event.preventDefault();
            event.stopPropagation();
            if (busy) return;
            const nextItems = currentItems.filter((entry) => entry.toLowerCase() !== item.toLowerCase());
            if (nextItems.length === currentItems.length) return;
            await persistTagChange(nextItems, "Skill removed", false);
          },
        }, "\u00d7"));
        tags.appendChild(tag);
      });
      tags.appendChild(addTag);
    };

    const persistTagChange = async (nextItems, successMessage, clearInput) => {
      setBusy(true);
      try {
        await quickEdit.onChange(nextItems);
        currentItems = normalizeTagList(nextItems);
        if (clearInput) addInput.value = "";
        renderTags();
        toast(successMessage, "success");
      } catch (err) {
        toast(getUserFacingErrorMessage(err, "Could not update skills."), "error");
      } finally {
        setBusy(false);
        addInput.focus();
      }
    };

    const handleAdd = async (rawValue = null) => {
      if (busy) return;
      const sourceValue = rawValue == null ? addInput.value : rawValue;
      const toAdd = normalizeTagList(csvList(sourceValue));
      if (!toAdd.length) return;
      const nextItems = normalizeTagList([...currentItems, ...toAdd]);
      if (nextItems.length === currentItems.length) {
        toast("Skill already present", "info");
        addInput.focus();
        return;
      }
      await persistTagChange(nextItems, `${toAdd.length} skill${toAdd.length > 1 ? "s" : ""} added`, true);
    };

    if (SKILL_AUTOCOMPLETE_FIELDS.has(name)) {
      wireSkillAutocomplete(addInput, {
        onSuggestionSelected: (selectedValue) => handleAdd(selectedValue),
      });
    }

    addInput.addEventListener("input", updateAddButtonState);
    addInput.addEventListener("keydown", (event) => {
      if (event.key !== "Enter") return;
      event.preventDefault();
      handleAdd();
    });
    addBtn.addEventListener("click", handleAdd);

    renderTags();
    updateAddButtonState();
  }
  return wrap;
}

/* === Helpers: display builders === */

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
function normalizeTagList(items) {
  const seen = new Set();
  const normalized = [];
  for (const item of items || []) {
    const clean = String(item || "").trim();
    if (!clean) continue;
    const key = clean.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    normalized.push(clean);
  }
  return normalized;
}

export {
  buildFieldMap,
  buildPartialProfile,
} from "../services/profile-import-service.js";

function wireSkillAutocomplete(input, options = {}) {
  const onSuggestionSelected = typeof options.onSuggestionSelected === "function"
    ? options.onSuggestionSelected
    : null;
  const listId = `skill-suggest-${Math.random().toString(36).slice(2, 10)}`;
  const dataList = el("datalist", { id: listId });
  input.setAttribute("list", listId);
  input.insertAdjacentElement("afterend", dataList);

  let pendingTimer = null;
  let token = 0;
  let selectingSuggestion = false;

  const maybeApplySelectedSuggestion = async () => {
    if (!onSuggestionSelected || selectingSuggestion) return;
    const selectedValue = String(input.value || "").trim();
    if (!selectedValue) return;
    const hasOption = [...dataList.options].some((option) => option.value === selectedValue);
    if (!hasOption) return;
    selectingSuggestion = true;
    try {
      await onSuggestionSelected(selectedValue);
    } finally {
      selectingSuggestion = false;
    }
  };

  input.addEventListener("input", (event) => {
    if (pendingTimer) clearTimeout(pendingTimer);
    pendingTimer = setTimeout(async () => {
      const currentToken = ++token;
      const currentValue = String(input.value || "");
      const chunks = currentValue.split(",");
      const lastChunk = chunks[chunks.length - 1]?.trim() || "";
      if (lastChunk.length < 2) {
        dataList.innerHTML = "";
        return;
      }
      const prefix = chunks.slice(0, -1).map((v) => v.trim()).filter(Boolean);
      const prefixText = prefix.length ? `${prefix.join(", ")}, ` : "";
      try {
        const suggestions = await suggestSkills(lastChunk, 8);
        if (currentToken !== token) return;
        dataList.innerHTML = "";
        (suggestions || []).forEach((skill) => {
          const label = Array.isArray(skill.aliases) && skill.aliases.length ? skill.aliases[0] : skill.name;
          dataList.appendChild(el("option", {
            value: `${prefixText}${skill.name || ""}`,
            label: label || skill.name || "",
          }));
        });
      } catch {
        // Silent fallback: profile editing remains usable without suggestions.
      }
    }, 180);

    if (!onSuggestionSelected) return;
    const isReplacement = typeof InputEvent !== "undefined"
      && event instanceof InputEvent
      && event.inputType === "insertReplacementText";
    if (isReplacement) {
      void maybeApplySelectedSuggestion();
    }
  });

  if (onSuggestionSelected) {
    input.addEventListener("change", () => {
      void maybeApplySelectedSuggestion();
    });
  }
}
