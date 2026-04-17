import { api } from "../api.js";
import { el, spinner, emptyState, toast } from "../ui.js";
import { fieldRow, labeledField, tagGroup, provBadge, arr } from "./profile.js";

export async function renderPublicProfile(container, { id }) {
  container.dataset.page = "profile";
  container.innerHTML = "";
  container.appendChild(spinner());

  let profile;
  try {
    profile = await api.get(`/api/v1/nodes/${id}/profile`);
  } catch (err) {
    container.querySelector(".spinner")?.remove();
    if (err.status === 404) {
      container.appendChild(emptyState("Profile not found or not public."));
    } else {
      container.appendChild(emptyState("Could not load profile."));
      toast(err.message, "error");
    }
    return;
  }

  container.querySelector(".spinner")?.remove();

  const identity = profile.identity || {};
  const displayName = identity.display_name || "Profile";

  const headerEl = el("header", { className: "profile-header" });
  const headerText = el("div", {});
  headerText.appendChild(el("h1", { className: "page-title" }, displayName));
  headerText.appendChild(el("p", { className: "page-subtitle text-secondary" }, "Public profile"));
  headerEl.appendChild(headerText);
  container.appendChild(headerEl);

  renderPublicProfileView(container, profile, id);
}

async function renderPublicProfileView(container, p, nodeId) {
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

  const grid = el("div", { className: "profile-grid" });

  /* === Identity === */
  const idCard = readonlySection("person", "Identity", "blue");
  idCard.classList.add("profile-grid-full");
  const idBody = idCard.querySelector(".profile-card-body");
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
    idBody.appendChild(avatarRow);
  }
  const emailField = el("div", { className: "profile-field" });
  const emailLabel = el("div", { className: "profile-field-label" }, "Email");
  if (prov["identity.email"]) emailLabel.appendChild(provBadge(prov["identity.email"]));
  emailField.appendChild(emailLabel);
  const emailValueWrap = el("div", {
    className: "profile-field-value",
    style: "display:flex;align-items:center;gap:0.4rem",
  });
  emailValueWrap.appendChild(document.createTextNode(identity.email || "\u2014"));
  if (identity.email) {
    const copyBtn = el("button", {
      className: "btn-ghost",
      style: "padding:0;border:none;background:none;cursor:pointer;display:flex;align-items:center",
      title: "Copy email",
      onClick: () => {
        navigator.clipboard.writeText(identity.email).catch(() => {});
        toast("Email copied: " + identity.email);
      },
    }, el("span", { className: "material-symbols-outlined", style: "font-size:16px;color:var(--text-tertiary)" }, "content_copy"));
    emailValueWrap.appendChild(copyBtn);
  }
  emailField.appendChild(emailValueWrap);

  const idFields = [
    labeledField("Display Name", identity.display_name, prov["identity.display_name"]),
    emailField,
  ];
  if (identity.company) {
    idFields.push(labeledField("Company", identity.company, prov["identity.company"]));
  }
  if (identity.birth_date) {
    idFields.push(labeledField("Birth Date", identity.birth_date, prov["identity.birth_date"]));
  }
  if (contacts.slack_handle) {
    const handle = contacts.slack_handle.replace(/^@/, "");
    const slackWrap = el("div", { className: "profile-field" });
    slackWrap.appendChild(el("div", { className: "profile-field-label" }, "Slack"));
    const slackLink = el("a", {
      href: `slack://user?team=&id=${handle}`,
      target: "_blank",
      rel: "noopener",
      style: "display:flex;align-items:center;gap:0.4rem;text-decoration:none;color:inherit",
    });
    slackLink.appendChild(document.createTextNode(contacts.slack_handle));
    const slackValue = el("div", { className: "profile-field-value" });
    slackValue.appendChild(slackLink);
    slackWrap.appendChild(slackValue);
    idFields.push(slackWrap);
  }
  if (contacts.linkedin_url) {
    const linkedinUrl = normalizeLinkedinUrl(contacts.linkedin_url);
    const linkedinWrap = el("div", { className: "profile-field" });
    linkedinWrap.appendChild(el("div", { className: "profile-field-label" }, "LinkedIn"));
    const linkedinLink = el("a", {
      href: linkedinUrl,
      target: "_blank",
      rel: "noopener",
      style: "display:flex;align-items:center;gap:0.4rem;text-decoration:none;color:inherit",
    }, linkedinUrl);
    const linkedinValue = el("div", { className: "profile-field-value" });
    linkedinValue.appendChild(linkedinLink);
    linkedinWrap.appendChild(linkedinValue);
    idFields.push(linkedinWrap);
  }
  idBody.appendChild(fieldRow(idFields));
  grid.appendChild(idCard);

  /* === Full-width: Location === */
  const locCard = readonlySection("language", "Location", "blue");
  locCard.classList.add("profile-grid-full");
  const locBody = locCard.querySelector(".profile-card-body");
  const location = [geo.city, geo.country].filter(Boolean).join(", ");
  locBody.appendChild(fieldRow([
    labeledField("Location", location || "\u2014"),
    ...(geo.timezone ? [labeledField("Timezone", geo.timezone)] : []),
  ]));
  grid.appendChild(locCard);

  /* === Full-width sections === */

  /* Professional */
  const profCard = readonlySection("business_center", "Professional", "purple");
  profCard.classList.add("profile-grid-full");
  const profBody = profCard.querySelector(".profile-card-body");
  profBody.appendChild(fieldRow([
    labeledField("Current Role", arr(prof.roles), prov["professional.roles"]),
    labeledField("Seniority", prof.seniority, prov["professional.seniority"]),
  ]));
  if (prof.skills_technical?.length)
    profBody.appendChild(tagGroup("Technical Skills", prof.skills_technical, "blue", prov["professional.skills_technical"]));
  if (prof.skills_soft?.length)
    profBody.appendChild(tagGroup("Soft Skills", prof.skills_soft, "purple", prov["professional.skills_soft"]));
  if (prof.tools_and_tech?.length)
    profBody.appendChild(tagGroup("Tools & Tech", prof.tools_and_tech, "blue", prov["professional.tools_and_tech"]));
  profBody.appendChild(fieldRow([
    labeledField("Work Mode", prof.work_mode_preference, prov["professional.work_mode_preference"]),
    labeledField("Employment", prof.employment_type, prov["professional.employment_type"]),
    labeledField("Languages", arr(prof.languages_spoken), prov["professional.languages_spoken"]),
  ]));
  if (prof.industries?.length)
    profBody.appendChild(tagGroup("Industries", prof.industries, "slate", prov["professional.industries"]));
  grid.appendChild(profCard);

  /* Skills */
  await renderPublicSkillsSection(grid, nodeId);

  /* Interests & Personal */
  const hasPersonal = personal.hobbies?.length || personal.sports?.length ||
    personal.causes?.length || interests.topics_frequent?.length ||
    interests.learning_areas?.length ||
    personal.music_genres?.length || personal.book_genres?.length ||
    personal.personality_tags?.length || personal.education?.length;

  if (hasPersonal) {
    const intCard = readonlySection("favorite", "Interests & Personal", "emerald");
    intCard.classList.add("profile-grid-full");
    const intBody = intCard.querySelector(".profile-card-body");
    if (personal.hobbies?.length || personal.sports?.length) {
      const hobbies = [...(personal.hobbies || []), ...(personal.sports || [])];
      intBody.appendChild(tagGroup("Hobbies & Sports", hobbies, "emerald"));
    }
    if (personal.causes?.length) intBody.appendChild(tagGroup("Causes", personal.causes, "purple"));
    if (interests.topics_frequent?.length)
      intBody.appendChild(tagGroup("Topics", interests.topics_frequent, "blue"));
    if (interests.learning_areas?.length)
      intBody.appendChild(tagGroup("Learning Areas", interests.learning_areas, "slate"));
    if (personal.music_genres?.length) intBody.appendChild(tagGroup("Music", personal.music_genres, "emerald"));
    if (personal.book_genres?.length) intBody.appendChild(tagGroup("Books", personal.book_genres, "purple"));
    if (personal.personality_tags?.length) intBody.appendChild(tagGroup("Personality", personal.personality_tags, "slate"));
    if (personal.education?.length) intBody.appendChild(tagGroup("Education", personal.education, "blue"));
    grid.appendChild(intCard);
  }

  container.appendChild(grid);
}

async function renderPublicSkillsSection(grid, nodeId) {
  let skills;
  try {
    skills = await api.get(`/api/v1/nodes/${nodeId}/skills`);
  } catch { return; }

  if (!skills?.length) return;

  const card = readonlySection("psychology", "Skills", "blue");
  card.classList.add("profile-grid-full");
  const body = card.querySelector(".profile-card-body");

  const grouped = {};
  for (const s of skills) {
    const cat = s.category || "Other";
    if (!grouped[cat]) grouped[cat] = [];
    grouped[cat].push(s);
  }

  for (const [category, items] of Object.entries(grouped)) {
    const catWrap = el("div", { className: "profile-tag-group" });
    catWrap.appendChild(el("div", { className: "profile-field-label" }, category));
    const tags = el("div", { className: "profile-tags" });
    for (const s of items) {
      const tag = el("span", { className: "profile-tag profile-tag--blue" });
      tag.appendChild(document.createTextNode(s.skill_name));
      if (s.level > 0) {
        tag.appendChild(el("span", {
          className: "skill-level-badge",
          title: `Level ${s.level}`,
        }, ` Lv${s.level}`));
      }
      if (s.interest) {
        tag.appendChild(el("span", {
          className: "skill-interest-dot",
          title: "Interested in growing",
        }, " \u2605"));
      }
      tags.appendChild(tag);
    }
    catWrap.appendChild(tags);
    body.appendChild(catWrap);
  }

  grid.appendChild(card);
}

function readonlySection(icon, title, color) {
  const card = el("div", { className: "profile-card" });
  const header = el("div", { className: "profile-card-header" });
  header.appendChild(el("h2", { className: "profile-card-title" },
    el("span", { className: `material-symbols-outlined profile-icon profile-icon--${color}` }, icon),
    title
  ));
  card.appendChild(header);
  card.appendChild(el("div", { className: "profile-card-body" }));
  return card;
}

function normalizeLinkedinUrl(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  if (/^https?:\/\//i.test(raw)) return raw;
  return `https://${raw}`;
}

