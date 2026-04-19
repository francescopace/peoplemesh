import { el } from "../ui.js";

function formatProvSource(src) {
  if (!src) return "Unknown";
  if (src.includes("cv_docling_llm")) return "CV";
  if (src.includes("github")) return "GitHub";
  if (src.includes("manual")) return "Manual";
  return src;
}

export function provBadge(src) {
  return el("span", { className: "profile-prov-badge" }, formatProvSource(src));
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
