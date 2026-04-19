export const GEO_POSITIVE = new Set(["same_country", "same_continent", "remote_friendly"]);

export function isPositiveGeoReason(geographyReason) {
  return !!(geographyReason && GEO_POSITIVE.has(geographyReason));
}

export function locationChipStyle(colors, geographyReason) {
  if (isPositiveGeoReason(geographyReason)) {
    return `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; padding:0.1rem 0.45rem; border-radius:4px; font-size:0.72rem`;
  }
  return "background:rgba(148,163,184,0.08); color:var(--color-gray-400); border:1px solid rgba(148,163,184,0.2); padding:0.1rem 0.45rem; border-radius:4px; font-size:0.72rem";
}

export function matchedTagStyle(colors) {
  return `background:${colors.bg}; color:${colors.color}; border:1px solid ${colors.border}; box-shadow:0 0 0 1px ${colors.border}`;
}
