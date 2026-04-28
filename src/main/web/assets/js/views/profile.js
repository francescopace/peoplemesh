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
  patchMyProfile,
} from "../services/profile-service.js";
import { suggestSkills } from "../services/skills-service.js";
import {
  fieldRow,
  labeledField,
  provBadge,
} from "../components/profile-fields.js";

const SENIORITY_OPTIONS = ["JUNIOR", "MID", "SENIOR", "LEAD", "EXECUTIVE"];
const WORK_MODE_OPTIONS = ["REMOTE", "HYBRID", "OFFICE", "FLEXIBLE"];
const EMPLOYMENT_OPTIONS = ["EMPLOYED", "FREELANCE", "FOUNDER", "LOOKING", "OPEN_TO_OFFERS"];
const SKILL_AUTOCOMPLETE_FIELDS = new Set(["skills", "softSkills", "toolsAndTech"]);
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

function toMergePatchPayload(partial) {
  if (Array.isArray(partial)) {
    return partial.map((item) => toMergePatchPayload(item));
  }
  if (partial && typeof partial === "object") {
    const out = {};
    Object.entries(partial).forEach(([key, value]) => {
      out[key] = value === undefined ? null : toMergePatchPayload(value);
    });
    return out;
  }
  return partial;
}

function applyPartialToProfile(p, partial) {
  if (partial.professional != null) {
    p.professional = { ...(p.professional || {}), ...partial.professional };
  }
  if (partial.personal != null) {
    p.personal = { ...(p.personal || {}), ...partial.personal };
  }
  if (partial.interests_professional != null) {
    p.interests_professional = { ...(p.interests_professional || {}), ...partial.interests_professional };
  }
  if (partial.contacts != null) {
    p.contacts = { ...(p.contacts || {}), ...partial.contacts };
  }
  if (partial.geography != null) {
    p.geography = { ...(p.geography || {}), ...partial.geography };
  }
  if (partial.identity != null) {
    p.identity = { ...(p.identity || {}), ...partial.identity };
  }
  if (partial.field_provenance != null) {
    p.field_provenance = { ...(p.field_provenance || {}), ...partial.field_provenance };
  }
}

async function persistProfileField(p, partial, successMessage = "Saved") {
  try {
    const patch = toMergePatchPayload(partial);
    const updated = await patchMyProfile(patch);
    if (updated && typeof updated === "object" && Object.keys(updated).length > 0) {
      Object.assign(p, updated);
    }
    applyPartialToProfile(p, patch);
    toast(successMessage, "success");
    return true;
  } catch (err) {
    toast(getUserFacingErrorMessage(err, "Could not save profile."), "error");
    return false;
  }
}

async function persistTagMutation(p, partial) {
  const patch = toMergePatchPayload(partial);
  const updated = await patchMyProfile(patch);
  if (updated && typeof updated === "object" && Object.keys(updated).length > 0) {
    Object.assign(p, updated);
  }
  applyPartialToProfile(p, patch);
  return updated;
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
  const profilePane = container;

  const grid = el("div", { className: "profile-grid" });

  /* Identity */
  const { card: identitySection, body: identityBody } = profileSection("person", "Identity", "blue");
  if (identity.photo_url) {
    const avatarRow = el("div", { className: "profile-identity-avatar" });
    const img = el("img", {
      src: identity.photo_url,
      alt: identity.display_name || "Profile photo",
      className: "profile-avatar-img",
      referrerpolicy: "no-referrer",
    });
    avatarRow.appendChild(img);
    if (prov["identity.photo_url"]) avatarRow.appendChild(provBadge(prov["identity.photo_url"]));
    identityBody.appendChild(avatarRow);
  }
  identityBody.appendChild(fieldRow([
    labeledField("Display Name", identity.display_name, prov["identity.display_name"]),
    labeledField("Email", identity.email, prov["identity.email"]),
  ]));
  identityBody.appendChild(fieldRow([
    labeledField("First Name", identity.first_name, prov["identity.first_name"]),
    labeledField("Last Name", identity.last_name, prov["identity.last_name"]),
  ]));
  identityBody.appendChild(fieldRow([
    labeledField("Company", identity.company, prov["identity.company"]),
    inlineScalarField({
      label: "Birth Date",
      value: identity.birth_date,
      provSrc: prov["identity.birth_date"],
      type: "date",
      profilePane,
      p,
      datasetField: "birthDate",
      buildPartial: (trimmed) => ({ identity: { birth_date: trimmed || undefined } }),
    }),
  ]));
  identitySection.classList.add("profile-grid-main");
  identitySection.style.gridRow = "1 / 3";
  grid.appendChild(identitySection);

  /* Location (under Identity) */
  const { card: locationSection, body: locationBody } = profileSection("language", "Location", "blue");
  locationBody.appendChild(fieldRow([
    inlineScalarField({
      label: "City",
      value: geo.city,
      profilePane,
      p,
      datasetField: "city",
      buildPartial: (trimmed) => ({ geography: { city: trimmed || undefined } }),
    }),
    inlinePairSelectField({
      label: "Country",
      pairs: COUNTRY_OPTIONS,
      currentValue: geo.country,
      provSrc: prov["geography.country"],
      profilePane,
      p,
      datasetField: "country",
      buildPartial: (code) => ({ geography: { country: code || undefined } }),
    }),
    inlineSelectField({
      label: "Timezone",
      options: TIMEZONE_OPTIONS,
      currentValue: geo.timezone,
      provSrc: prov["geography.timezone"],
      profilePane,
      p,
      datasetField: "timezone",
      buildPartial: (tz) => ({ geography: { timezone: tz || undefined } }),
    }),
  ]));
  locationSection.classList.add("profile-grid-full");
  grid.appendChild(locationSection);

  /* Contacts */
  const slackProv = prov["contacts.slack_handle"] || prov["professional.slack_handle"];
  const tgProv = prov["contacts.telegram_handle"] || prov["professional.telegram_handle"];
  const phoneProv = prov["contacts.mobile_phone"] || prov["professional.mobile_phone"];
  const liProv = prov["contacts.linkedin_url"] || prov["professional.linkedin_url"];

  const { card: contactsSection, body: contactsBody } = profileSection("contact_phone", "Contacts", "purple");
  contactsBody.appendChild(fieldRow([
    inlineScalarField({
      label: "Slack",
      value: contacts.slack_handle,
      provSrc: slackProv,
      profilePane,
      p,
      datasetField: "slackHandle",
      formatDisplay: (v) => formatSlackDisplay(v),
      buildPartial: (trimmed) => ({ contacts: { slack_handle: trimmed || undefined } }),
    }),
    inlineScalarField({
      label: "Telegram",
      value: contacts.telegram_handle,
      provSrc: tgProv,
      profilePane,
      p,
      datasetField: "telegramHandle",
      formatDisplay: (v) => formatTelegramDisplay(v),
      trailingExtras: (v) => telegramOpenLinkNodes(v),
      buildPartial: (trimmed) => ({ contacts: { telegram_handle: trimmed || undefined } }),
    }),
    inlineScalarField({
      label: "Mobile",
      value: contacts.mobile_phone,
      provSrc: phoneProv,
      type: "tel",
      profilePane,
      p,
      datasetField: "mobilePhone",
      trailingExtras: (v) => phoneTelLinkNodes(v),
      buildPartial: (trimmed) => ({ contacts: { mobile_phone: trimmed || undefined } }),
    }),
    inlineScalarField({
      label: "LinkedIn",
      value: contacts.linkedin_url,
      provSrc: liProv,
      type: "url",
      profilePane,
      p,
      datasetField: "linkedinUrl",
      formatDisplay: (v) => (v ? normalizeLinkedinUrl(v) : ""),
      trailingExtras: (v) => linkedinOpenLinkNodes(v),
      buildPartial: (trimmed) => ({ contacts: { linkedin_url: trimmed || undefined } }),
    }),
  ]));
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
  const roleVal = Array.isArray(prof.roles) ? (prof.roles[0] || "") : (prof.roles || "");
  const { card: profSection, body: profBody } = profileSection("business_center", "Professional", "purple");
  profBody.appendChild(fieldRow([
    inlineScalarField({
      label: "Current Role",
      value: roleVal,
      provSrc: prov["professional.roles"],
      profilePane,
      p,
      datasetField: "roles",
      buildPartial: (trimmed) => ({ professional: { roles: trimmed ? [trimmed] : [] } }),
    }),
    inlineSelectField({
      label: "Seniority",
      options: SENIORITY_OPTIONS,
      currentValue: prof.seniority,
      provSrc: prov["professional.seniority"],
      profilePane,
      p,
      datasetField: "seniority",
      buildPartial: (v) => ({ professional: { seniority: v || undefined } }),
    }),
  ]));
  profBody.appendChild(chipTagListField({
    label: "Technical Skills",
    getItems: () => p.professional?.skills_technical,
    color: "blue",
    provSrc: prov["professional.skills_technical"],
    p,
    placeholder: "Add a technical skill",
    skillAutocompleteName: "skills",
    partialBuilder: (items) => ({ professional: { skills_technical: items } }),
  }));
  profBody.appendChild(chipTagListField({
    label: "Soft Skills",
    getItems: () => p.professional?.skills_soft,
    color: "purple",
    provSrc: prov["professional.skills_soft"],
    p,
    placeholder: "Add a soft skill",
    skillAutocompleteName: "softSkills",
    partialBuilder: (items) => ({ professional: { skills_soft: items } }),
  }));
  profBody.appendChild(chipTagListField({
    label: "Tools & Tech",
    getItems: () => p.professional?.tools_and_tech,
    color: "blue",
    provSrc: prov["professional.tools_and_tech"],
    p,
    placeholder: "Add a tool or technology",
    skillAutocompleteName: "toolsAndTech",
    partialBuilder: (items) => ({ professional: { tools_and_tech: items } }),
  }));
  profBody.appendChild(fieldRow([
    inlineSelectField({
      label: "Work Mode",
      options: WORK_MODE_OPTIONS,
      currentValue: prof.work_mode_preference,
      provSrc: prov["professional.work_mode_preference"],
      profilePane,
      p,
      datasetField: "workMode",
      buildPartial: (v) => ({ professional: { work_mode_preference: v || undefined } }),
    }),
    inlineSelectField({
      label: "Employment",
      options: EMPLOYMENT_OPTIONS,
      currentValue: prof.employment_type,
      provSrc: prov["professional.employment_type"],
      profilePane,
      p,
      datasetField: "employmentType",
      buildPartial: (v) => ({ professional: { employment_type: v || undefined } }),
    }),
  ]));
  profBody.appendChild(chipTagListField({
    label: "Languages Spoken",
    getItems: () => p.professional?.languages_spoken,
    color: "slate",
    provSrc: prov["professional.languages_spoken"],
    p,
    placeholder: "Add a language",
    skillAutocompleteName: null,
    partialBuilder: (items) => ({ professional: { languages_spoken: items } }),
  }));
  profBody.appendChild(chipTagListField({
    label: "Industries",
    getItems: () => p.professional?.industries,
    color: "slate",
    provSrc: prov["professional.industries"],
    p,
    placeholder: "Add an industry",
    skillAutocompleteName: null,
    partialBuilder: (items) => ({ professional: { industries: items } }),
  }));
  profSection.classList.add("profile-grid-full");
  grid.appendChild(profSection);

  /* Interests & Personal */
  const hasPersonalContent = personal.hobbies?.length || personal.sports?.length ||
    personal.causes?.length || interests.learning_areas?.length ||
    interests.project_types?.length ||
    personal.music_genres?.length || personal.book_genres?.length ||
    personal.personality_tags?.length || personal.education?.length;

  if (hasPersonalContent || canEdit) {
    const { card: interestsSection, body: interestsBody } = profileSection("favorite", "Interests & Personal", "emerald");
    interestsBody.appendChild(chipTagListField({
      label: "Hobbies",
      getItems: () => p.personal?.hobbies,
      color: "emerald",
      provSrc: null,
      p,
      placeholder: "Add a hobby",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { hobbies: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Sports",
      getItems: () => p.personal?.sports,
      color: "emerald",
      provSrc: null,
      p,
      placeholder: "Add a sport",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { sports: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Causes",
      getItems: () => p.personal?.causes,
      color: "purple",
      provSrc: null,
      p,
      placeholder: "Add a cause",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { causes: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Learning Areas",
      getItems: () => p.interests_professional?.learning_areas,
      color: "slate",
      provSrc: prov["interests_professional.learning_areas"],
      p,
      placeholder: "Add a learning area",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ interests_professional: { learning_areas: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Project Types",
      getItems: () => p.interests_professional?.project_types,
      color: "blue",
      provSrc: null,
      p,
      placeholder: "Add a project type",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ interests_professional: { project_types: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Music",
      getItems: () => p.personal?.music_genres,
      color: "emerald",
      provSrc: null,
      p,
      placeholder: "Add a genre",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { music_genres: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Books",
      getItems: () => p.personal?.book_genres,
      color: "purple",
      provSrc: null,
      p,
      placeholder: "Add a book genre",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { book_genres: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Personality",
      getItems: () => p.personal?.personality_tags,
      color: "slate",
      provSrc: null,
      p,
      placeholder: "Add a tag",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { personality_tags: items } }),
    }));
    interestsBody.appendChild(chipTagListField({
      label: "Education",
      getItems: () => p.personal?.education,
      color: "blue",
      provSrc: null,
      p,
      placeholder: "Add education",
      skillAutocompleteName: null,
      partialBuilder: (items) => ({ personal: { education: items } }),
    }));
    interestsSection.classList.add("profile-grid-full");
    grid.appendChild(interestsSection);
  }

  container.appendChild(grid);
}

/* === Profile section shell & per-field editors === */

function normalizeLinkedinUrl(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  if (/^https?:\/\//i.test(raw)) return raw;
  return `https://${raw}`;
}

function profileSection(icon, title, color) {
  const card = el("div", { className: "profile-card" });
  const header = el("div", { className: "profile-card-header" });
  header.appendChild(el("h2", { className: "profile-card-title" },
    el("span", { className: `material-symbols-outlined profile-icon profile-icon--${color}` }, icon),
    title
  ));
  card.appendChild(header);
  const body = el("div", { className: "profile-card-body" });
  card.appendChild(body);
  return { card, body };
}

function formatSlackDisplay(v) {
  const s = String(v || "").trim();
  if (!s) return "";
  const clean = s.replace(/^@/, "");
  return clean ? `@${clean}` : s;
}

function formatTelegramDisplay(v) {
  return formatSlackDisplay(v);
}

function telegramOpenLinkNodes(handle) {
  const clean = String(handle || "").replace(/^@/, "").trim();
  if (!clean) return [];
  const a = el("a", {
    href: `https://t.me/${encodeURIComponent(clean)}`,
    className: "profile-field-open-link",
    target: "_blank",
    rel: "noopener",
    "aria-label": "Open in Telegram",
  });
  a.addEventListener("click", (e) => e.stopPropagation());
  a.appendChild(el("span", { className: "material-symbols-outlined icon-18" }, "open_in_new"));
  return [a];
}

function phoneTelLinkNodes(phone) {
  if (!phone) return [];
  const normalized = String(phone).replace(/\s+/g, "");
  if (!normalized) return [];
  const a = el("a", {
    href: `tel:${encodeURIComponent(normalized)}`,
    className: "profile-field-open-link",
    "aria-label": "Call",
  });
  a.addEventListener("click", (e) => e.stopPropagation());
  a.appendChild(el("span", { className: "material-symbols-outlined icon-18" }, "call"));
  return [a];
}

function linkedinOpenLinkNodes(url) {
  if (!url) return [];
  const href = normalizeLinkedinUrl(url);
  if (!href) return [];
  const a = el("a", {
    href,
    className: "profile-field-open-link",
    target: "_blank",
    rel: "noopener",
    "aria-label": "Open LinkedIn profile",
  });
  a.addEventListener("click", (e) => e.stopPropagation());
  a.appendChild(el("span", { className: "material-symbols-outlined icon-18" }, "open_in_new"));
  return [a];
}

function inlineScalarField({
  label, value, provSrc, type = "text", profilePane, p, datasetField,
  buildPartial, formatDisplay, trailingExtras = [],
}) {
  const wrap = el("div", { className: "profile-field profile-field--click-edit" });
  if (datasetField) wrap.dataset.profileField = datasetField;
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const slot = el("div", { className: "profile-field-control-slot" });
  let currentValue = value;

  const viewText = () => {
    if (formatDisplay) return formatDisplay(currentValue) || "";
    return currentValue != null && String(currentValue).trim() !== "" ? String(currentValue).trim() : "";
  };

  const resolveTrailingExtras = () => {
    if (typeof trailingExtras === "function") {
      const dynamicExtras = trailingExtras(currentValue);
      return Array.isArray(dynamicExtras) ? dynamicExtras : [];
    }
    return Array.isArray(trailingExtras) ? trailingExtras : [];
  };

  function mountView() {
    slot.innerHTML = "";
    const row = el("div", { className: "profile-field-control-row" });
    const viewBtn = el("button", {
      type: "button",
      className: "profile-field-value profile-field-value--interactive",
      "aria-label": `Edit ${label}`,
    }, viewText() || "\u2014");
    viewBtn.addEventListener("click", () => mountEdit());
    row.appendChild(viewBtn);
    const extras = resolveTrailingExtras();
    if (extras.length) {
      const ex = el("div", { className: "profile-field-value-extras" });
      extras.forEach((n) => ex.appendChild(n));
      row.appendChild(ex);
    }
    slot.appendChild(row);
  }

  function mountEdit() {
    slot.innerHTML = "";
    const input = el("input", {
      className: "profile-editable-value profile-editable-value--block",
      type,
      value: currentValue || "",
    });
    const initialTrimmed = (currentValue != null ? String(currentValue) : "").trim();
    let cancelled = false;
    let saving = false;

    const persistOnBlur = async () => {
      if (cancelled || saving) return;
      const trimmed = (input.value || "").trim();
      if (trimmed === initialTrimmed) {
        mountView();
        return;
      }
      saving = true;
      input.disabled = true;
      const ok = await persistProfileField(p, buildPartial(trimmed), "Saved");
      if (!ok) {
        saving = false;
        input.disabled = false;
        input.focus();
        return;
      }
      currentValue = trimmed;
      mountView();
    };

    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        input.blur();
      }
      if (e.key === "Escape") {
        e.preventDefault();
        cancelled = true;
        mountView();
      }
    });
    input.addEventListener("blur", () => {
      void persistOnBlur();
    });
    slot.appendChild(input);
    input.focus();
    if (typeof input.select === "function") input.select();
  }

  mountView();
  wrap.appendChild(slot);
  return wrap;
}

function inlineSelectField({
  label, options, currentValue, provSrc, profilePane, p, datasetField, buildPartial,
}) {
  const wrap = el("div", { className: "profile-field profile-field--click-edit" });
  if (datasetField) wrap.dataset.profileField = datasetField;
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const slot = el("div", { className: "profile-field-control-slot" });
  let selectedValue = currentValue;

  const display = () => (selectedValue ? String(selectedValue) : "");

  function mountView() {
    slot.innerHTML = "";
    const viewBtn = el("button", {
      type: "button",
      className: "profile-field-value profile-field-value--interactive",
      "aria-label": `Edit ${label}`,
    }, display() || "\u2014");
    viewBtn.addEventListener("click", () => mountEdit());
    slot.appendChild(viewBtn);
  }

  function mountEdit() {
    slot.innerHTML = "";
    const select = el("select", {
      className: "profile-editable-value profile-editable-value--select profile-editable-value--block",
    });
    select.appendChild(el("option", { value: "" }, "Select\u2026"));
    options.forEach((opt) => {
      const o = el("option", { value: opt }, opt);
      if (opt === selectedValue) o.selected = true;
      select.appendChild(o);
    });
    const initial = (selectedValue != null ? String(selectedValue) : "").trim();
    let cancelled = false;
    let saving = false;
    select.addEventListener("change", () => {
      void (async () => {
        if (cancelled || saving) return;
        const v = (select.value || "").trim();
        if (v === initial) {
          mountView();
          return;
        }
        saving = true;
        select.disabled = true;
        const ok = await persistProfileField(p, buildPartial(v), "Saved");
        if (!ok) {
          saving = false;
          select.disabled = false;
          select.focus();
          return;
        }
        selectedValue = v;
        mountView();
      })();
    });
    select.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        cancelled = true;
        mountView();
      }
    });
    select.addEventListener("blur", () => {
      window.setTimeout(() => {
        if (!slot.isConnected || cancelled || saving) return;
        mountView();
      }, 0);
    });
    slot.appendChild(select);
    select.focus();
  }

  mountView();
  wrap.appendChild(slot);
  return wrap;
}

function inlinePairSelectField({
  label, pairs, currentValue, provSrc, profilePane, p, datasetField, buildPartial,
}) {
  const wrap = el("div", { className: "profile-field profile-field--click-edit" });
  if (datasetField) wrap.dataset.profileField = datasetField;
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const slot = el("div", { className: "profile-field-control-slot" });
  let selectedValue = currentValue;

  const display = () => {
    const row = pairs.find(([v]) => v === selectedValue);
    return row ? `${row[1]} (${selectedValue})` : (selectedValue ? String(selectedValue) : "");
  };

  function mountView() {
    slot.innerHTML = "";
    const viewBtn = el("button", {
      type: "button",
      className: "profile-field-value profile-field-value--interactive",
      "aria-label": `Edit ${label}`,
    }, display() || "\u2014");
    viewBtn.addEventListener("click", () => mountEdit());
    slot.appendChild(viewBtn);
  }

  function mountEdit() {
    slot.innerHTML = "";
    const select = el("select", {
      className: "profile-editable-value profile-editable-value--select profile-editable-value--block",
    });
    select.appendChild(el("option", { value: "" }, "Select\u2026"));
    pairs.forEach(([v, lbl]) => {
      const o = el("option", { value: v }, `${lbl} (${v})`);
      if (v === selectedValue) o.selected = true;
      select.appendChild(o);
    });
    const initial = (selectedValue != null ? String(selectedValue) : "").trim();
    let cancelled = false;
    let saving = false;
    select.addEventListener("change", () => {
      void (async () => {
        if (cancelled || saving) return;
        const v = (select.value || "").trim();
        if (v === initial) {
          mountView();
          return;
        }
        saving = true;
        select.disabled = true;
        const ok = await persistProfileField(p, buildPartial(v), "Saved");
        if (!ok) {
          saving = false;
          select.disabled = false;
          select.focus();
          return;
        }
        selectedValue = v;
        mountView();
      })();
    });
    select.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        e.preventDefault();
        cancelled = true;
        mountView();
      }
    });
    select.addEventListener("blur", () => {
      window.setTimeout(() => {
        if (!slot.isConnected || cancelled || saving) return;
        mountView();
      }, 0);
    });
    slot.appendChild(select);
    select.focus();
  }

  mountView();
  wrap.appendChild(slot);
  return wrap;
}

function chipTagListField({
  label, getItems, color, provSrc, p, placeholder, skillAutocompleteName, partialBuilder,
}) {
  const wrap = el("div", {
    className: "profile-tag-group",
    dataset: { profileInlineTags: "true" },
  });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const tags = el("div", { className: "profile-tags" });
  wrap.appendChild(tags);

  let busy = false;
  let currentItems = normalizeTagList(getItems() || []);

  const addTag = el("div", { className: `profile-tag profile-tag--add profile-tag--${color}` });
  const addInput = el("input", {
    className: "profile-tag-add-input",
    type: "text",
    placeholder: placeholder || "Add",
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
          await persistTagChange(nextItems, "Removed", false);
        },
      }, "\u00d7"));
      tags.appendChild(tag);
    });
    tags.appendChild(addTag);
  };

  const persistTagChange = async (nextItems, successMessage, clearInput) => {
    setBusy(true);
    try {
      await persistTagMutation(p, partialBuilder(normalizeTagList(nextItems)));
      currentItems = normalizeTagList(getItems() || []);
      if (clearInput) addInput.value = "";
      renderTags();
      toast(successMessage, "success");
    } catch (err) {
      toast(getUserFacingErrorMessage(err, "Could not save profile."), "error");
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
      toast("Already in list", "info");
      addInput.focus();
      return;
    }
    const noun = toAdd.length > 1 ? "entries" : "entry";
    await persistTagChange(nextItems, `${toAdd.length} ${noun} added`, true);
  };

  if (skillAutocompleteName && SKILL_AUTOCOMPLETE_FIELDS.has(skillAutocompleteName)) {
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
  addBtn.addEventListener("click", () => handleAdd());

  renderTags();
  updateAddButtonState();
  return wrap;
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
