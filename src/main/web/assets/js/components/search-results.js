import { el, emptyState } from "../ui.js";
import { NODE_TYPE_ICONS, NODE_TYPE_COLORS } from "../node-types.js";
import { contactFooter } from "../contact-actions.js";
import { termsMatch } from "../utils/term-matching.js";
import {
  GEO_POSITIVE,
  locationChipStyle,
  matchedTagStyle,
} from "../utils/match-visuals.js";
import {
  CARD_BADGE_LIMITS,
  buildScoreWithTooltip,
  findSkillLevel,
  sortTermsByMatchPriority,
} from "../utils/match-card-utils.js";

export function renderSearchResults(
  container,
  results,
  typeFilter,
  countryFilter,
  hasMore,
  hasSearched,
  onLoadMore
) {
  container.innerHTML = "";

  if (!results || results.length === 0) {
    if (hasSearched && (typeFilter || countryFilter)) {
      container.appendChild(emptyState("No results match the selected filters."));
    } else {
      container.appendChild(emptyState("No results found. Try a different query."));
    }
    return;
  }

  let filtered = results;
  if (typeFilter) {
    filtered = filtered.filter((r) =>
      typeFilter === "PEOPLE"
        ? (r.resultType === "profile" || r.nodeType === "PEOPLE")
        : r.nodeType === typeFilter
    );
  }
  if (countryFilter) {
    filtered = filtered.filter((r) => r.country === countryFilter);
  }

  if (!filtered.length) {
    container.appendChild(emptyState("No results match the selected filters."));
    return;
  }

  const grid = el("div", { className: "search-results-grid" });
  filtered.forEach((r) => {
    if (r.resultType === "profile") {
      grid.appendChild(renderProfileCard(r));
    } else {
      grid.appendChild(renderNodeCard(r));
    }
  });
  container.appendChild(grid);

  if (hasMore) {
    const loadMoreBtn = el("button", {
      className: "btn btn-secondary",
      type: "button",
      onClick: onLoadMore,
    }, "Load more");
    container.appendChild(el("div", { className: "search-load-more" }, loadMoreBtn));
  }
}

function renderProfileCard(result) {
  const colors = NODE_TYPE_COLORS.PEOPLE;
  const card = el("div", { className: "discover-card" });
  card.style.borderLeft = `3px solid ${colors.border}`;

  const hdr = el("div", { className: "dc-header" });
  const avatarWrap = el("div", { className: "dc-avatar-wrap" });

  const fallbackEl = () => el("div", {
    className: "dc-avatar dc-avatar--icon",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, el("span", { className: "material-symbols-outlined" }, "person"));

  if (result.avatarUrl) {
    const img = el("img", {
      className: "dc-avatar dc-avatar--photo",
      src: result.avatarUrl,
      alt: result.displayName || "",
      referrerPolicy: "no-referrer",
    });
    img.onerror = () => img.replaceWith(fallbackEl());
    avatarWrap.appendChild(img);
  } else {
    avatarWrap.appendChild(fallbackEl());
  }

  const info = el("div", { className: "dc-info" });
  const nameLink = el("a", {
    href: `#/people/${result.id}`,
    className: "dc-name-link",
    style: "text-decoration:none;color:inherit",
  }, result.displayName || "Anonymous");
  info.appendChild(el("h3", { className: "dc-name" }, nameLink));

  const subtitle = el("div", { className: "dc-subtitle" });
  subtitle.appendChild(el("span", {
    className: "dc-type-badge",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, "PROFILE"));
  const locationParts = [result.city, result.country].filter(Boolean);
  if (locationParts.length) {
    const geoReason = result.breakdown?.geographyReason;
    const locStyle = locationChipStyle(colors, geoReason);
    subtitle.appendChild(el("span", { className: "dc-sep" }, "\u00B7"));
    subtitle.appendChild(el("span", { style: locStyle }, locationParts.join(", ")));
  }
  info.appendChild(subtitle);
  avatarWrap.appendChild(info);
  hdr.appendChild(avatarWrap);

  hdr.appendChild(buildScoreWithTooltip(result.score, colors, result.breakdown));
  card.appendChild(hdr);

  if (result.roles?.length) {
    card.appendChild(el("p", { className: "dc-meta" }, result.roles.join(" \u00b7 ")));
  }

  const allSkills = [...new Set([
    ...(result.skillsTechnical || []),
    ...(result.toolsAndTech || []),
  ].map((s) => s.trim()))];
  if (allSkills.length) {
    const matched = [
      ...(result.breakdown?.matchedMustHaveSkills || []),
      ...(result.breakdown?.matchedNiceToHaveSkills || []),
    ];
    const skillLevels = result.skill_levels || {};
    const matchStyle = matchedTagStyle(colors);
    const tagsArea = el("div", { className: "dc-tags-area" });
    const row = el("div", { className: "dc-tags" });
    const sortedSkills = sortTermsByMatchPriority(allSkills, matched);
    const visibleSkills = sortedSkills.slice(0, CARD_BADGE_LIMITS.search.profileSkills);
    visibleSkills.forEach((s) => {
      const isMatch = matched.some((m) => termsMatch(m, s));
      const tag = el("span", { className: "dc-tag", style: isMatch ? matchStyle : undefined }, s);
      const lvl = findSkillLevel(skillLevels, s);
      if (lvl > 0) {
        tag.appendChild(el("span", { className: "skill-level-badge" }, `Lv${lvl}`));
      }
      row.appendChild(tag);
    });
    if (sortedSkills.length > visibleSkills.length) {
      row.appendChild(el("span", { className: "dc-tag dc-tag--more" }, `+${sortedSkills.length - visibleSkills.length} more tags`));
    }
    appendGeoTag(row, result.breakdown?.geographyReason, matchStyle);
    tagsArea.appendChild(row);
    card.appendChild(tagsArea);
  }

  const actions = contactFooter(
    result.slackHandle,
    result.email,
    result.telegramHandle,
    result.mobilePhone,
    result.linkedinUrl
  );
  if (actions) card.appendChild(actions);

  return card;
}

function renderNodeCard(result) {
  const nType = result.nodeType || "COMMUNITY";
  const iconName = NODE_TYPE_ICONS[nType] || "layers";
  const colors = NODE_TYPE_COLORS[nType] || NODE_TYPE_COLORS.JOB;

  const card = el("div", { className: "discover-card" });
  card.style.borderLeft = `3px solid ${colors.border}`;

  const hdr = el("div", { className: "dc-header" });
  const avatarWrap = el("div", { className: "dc-avatar-wrap" });
  avatarWrap.appendChild(el("div", {
    className: "dc-avatar dc-avatar--icon",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, el("span", { className: "material-symbols-outlined" }, iconName)));

  const info = el("div", { className: "dc-info" });
  const nodeTitle = result.title || "Untitled";
  if (nType === "JOB") {
    const jobLink = el("a", {
      href: `#/jobs/${result.id}`,
      className: "dc-name-link",
      style: "text-decoration:none;color:inherit",
    }, nodeTitle);
    info.appendChild(el("h3", { className: "dc-name" }, jobLink));
  } else {
    info.appendChild(el("h3", { className: "dc-name" }, nodeTitle));
  }

  const subtitle = el("div", { className: "dc-subtitle" });
  subtitle.appendChild(el("span", {
    className: "dc-type-badge",
    style: `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}`,
  }, nType.replace("_", " ")));
  if (result.country) {
    const geoReason = result.breakdown?.geographyReason;
    const locStyle = locationChipStyle(colors, geoReason);
    subtitle.appendChild(el("span", { className: "dc-sep" }, "\u00B7"));
    subtitle.appendChild(el("span", { style: locStyle }, result.country));
  }
  info.appendChild(subtitle);
  avatarWrap.appendChild(info);
  hdr.appendChild(avatarWrap);

  hdr.appendChild(buildScoreWithTooltip(result.score, colors, result.breakdown));
  card.appendChild(hdr);

  if (result.description) {
    const desc = result.description.length > 120
      ? result.description.substring(0, 120) + "\u2026" : result.description;
    card.appendChild(el("p", { className: "dc-desc" }, desc));
  }

  if (result.tags?.length) {
    const matchedTerms = result.breakdown?.matchedMustHaveSkills || [];
    const matchStyle = matchedTagStyle(colors);
    const tagsArea = el("div", { className: "dc-tags-area" });
    const row = el("div", { className: "dc-tags" });
    const sortedTags = sortTermsByMatchPriority(result.tags, matchedTerms);
    const visibleTags = sortedTags.slice(0, CARD_BADGE_LIMITS.search.nodeTags);
    visibleTags.forEach((t) => {
      const isMatch = matchedTerms.some((m) => termsMatch(m, t));
      row.appendChild(el("span", { className: "dc-tag", style: isMatch ? matchStyle : undefined }, t));
    });
    if (sortedTags.length > visibleTags.length) {
      row.appendChild(el("span", { className: "dc-tag dc-tag--more" }, `+${sortedTags.length - visibleTags.length} more tags`));
    }
    appendGeoTag(row, result.breakdown?.geographyReason, matchStyle);
    tagsArea.appendChild(row);
    card.appendChild(tagsArea);
  }

  return card;
}

function appendGeoTag(row, geographyReason, style) {
  if (!geographyReason || !GEO_POSITIVE.has(geographyReason)) {
    return;
  }
  row.appendChild(el("span", { className: "dc-tag", style }, geographyReason.replace(/_/g, " ")));
}
