import { el } from "./ui.js";

export function renderBrandElement({
  wrapperTag = "a",
  href = "#/",
  className,
  ariaLabel = "",
  iconClass,
  textClass = "",
  text = "PeopleMesh",
  organizationName = "",
  organizationClass = "",
  organizationSeparator = "| ",
} = {}) {
  const icon = el("span", { className: iconClass, "aria-hidden": "true" },
    el("i", { className: "fa-solid fa-network-wired" })
  );

  const textAttrs = textClass ? { className: textClass } : {};
  const textSpan = el("span", textAttrs, text);

  const linkAttrs = { className };
  if (wrapperTag === "a") linkAttrs.href = href;
  if (ariaLabel) linkAttrs["aria-label"] = ariaLabel;
  const link = el(wrapperTag, linkAttrs, icon, textSpan);
  const children = [link];

  const normalizedOrganizationName = typeof organizationName === "string" ? organizationName.trim() : "";
  if (normalizedOrganizationName) {
    const organizationAttrs = organizationClass ? { className: organizationClass } : {};
    const organizationLabel = `${organizationSeparator}${normalizedOrganizationName}`;
    children.push(el("span", organizationAttrs, organizationLabel));
  }

  const wrapper = el("span", { style: "display:inline-flex;align-items:center;white-space:nowrap" }, ...children);
  return wrapper;
}

export function renderBrand(opts = {}) {
  const element = renderBrandElement(opts);
  const wrapper = document.createElement("div");
  wrapper.appendChild(element);
  return wrapper.innerHTML;
}
