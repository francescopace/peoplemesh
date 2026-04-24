import { el } from "../ui.js";

const SOURCE_LABELS = Object.freeze({
  cv_docling_llm: "CV",
  github: "GitHub",
  manual: "Manual",
  google: "Google",
  microsoft: "Microsoft",
});

export function formatProvenanceSource(src) {
  if (!src) return "Unknown";
  const raw = String(src).trim();
  if (!raw) return "Unknown";
  const normalized = raw.toLowerCase();
  return SOURCE_LABELS[normalized] || raw;
}

export function provBadge(src) {
  return el("span", { className: "profile-prov-badge" }, formatProvenanceSource(src));
}

export function fieldRow(fields) {
  const cols = fields.length;
  const row = el("div", { className: `profile-field-row profile-field-row--${cols}` });
  fields.forEach((field) => row.appendChild(field));
  return row;
}

export function labeledField(label, value, provSrc) {
  const wrap = el("div", { className: "profile-field" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  wrap.appendChild(el("div", { className: "profile-field-value" }, value || "\u2014"));
  return wrap;
}

export function tagGroup(label, items, color, provSrc) {
  const wrap = el("div", { className: "profile-tag-group" });
  const labelEl = el("div", { className: "profile-field-label" }, label);
  if (provSrc) labelEl.appendChild(provBadge(provSrc));
  wrap.appendChild(labelEl);
  const tags = el("div", { className: "profile-tags" });
  (Array.isArray(items) ? items : []).forEach((item) => {
    tags.appendChild(el("span", { className: `profile-tag profile-tag--${color}` }, item));
  });
  wrap.appendChild(tags);
  return wrap;
}

export function arr(value) {
  return Array.isArray(value) ? value.join(", ") : (value || "");
}
