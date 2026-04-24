import { renderBrand } from "./brand.js";

const AUTH_NAV_ITEMS = [
  {
    path: "/search",
    label: "Search",
    iconClass: "fa-magnifying-glass",
    mobileIcon: "search",
    includeInBottomNav: true,
    tooltip: "Search across your organization.",
  },
  {
    path: "/my-mesh",
    label: "My Mesh",
    iconClass: "fa-users",
    mobileIcon: "hub",
    includeInBottomNav: true,
    tooltip: "Discover what matches your profile.",
  },
];

const TOP_BAR_VARIANTS = {
  app: {
    wrapperTag: "header",
    wrapperClass: "app-header",
    wrapperAttrs: "",
    containerClass: "",
    leftClass: "app-header-left",
    centerClass: "app-header-center",
    rightClass: "app-header-right",
    brand: {
      className: "app-brand-link",
      ariaLabel: "Go to PeopleMesh landing",
      iconClass: "app-brand-icon",
      textClass: "app-brand-text",
      organizationClass: "app-brand-organization",
    },
  },
  landing: {
    wrapperTag: "nav",
    wrapperClass: "landing-nav",
    wrapperAttrs: ' id="landing-nav"',
    containerClass: "container",
    leftClass: "nav-left",
    centerClass: "nav-center",
    rightClass: "nav-right",
    brand: {
      wrapperTag: "div",
      className: "nav-logo",
      iconClass: "nav-logo-icon",
      organizationClass: "nav-logo-organization",
    },
  },
  legal: {
    wrapperTag: "header",
    wrapperClass: "legal-topbar",
    wrapperAttrs: "",
    containerClass: "",
    leftClass: "legal-topbar-left",
    centerClass: "",
    rightClass: "legal-topbar-right",
    brand: {
      className: "app-brand-link",
      ariaLabel: "Go to PeopleMesh home",
      iconClass: "app-brand-icon",
      textClass: "app-brand-text",
      organizationClass: "app-brand-organization",
    },
  },
};

export function getAuthenticatedTopBarNavItems(user) {
  const items = [...AUTH_NAV_ITEMS];
  if (user?.entitlements?.is_admin) {
    items.push({
      path: "/admin",
      label: "Admin",
      iconClass: "fa-user-shield",
      mobileIcon: "admin_panel_settings",
      includeInBottomNav: false,
      tooltip: "Open administration tools.",
    });
  }
  return items;
}

function escAttr(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function renderTopBarNavLinks(items) {
  return items.map(
    (item) => `
      <a href="#${item.path}" class="header-nav-link" data-path="${item.path}"${item.tooltip ? ` data-tooltip="${escAttr(item.tooltip)}"` : ""}>
        <span class="topbar-nav-icon" aria-hidden="true">
          <i class="fa-solid ${item.iconClass || ""}"></i>
        </span>
        <span class="topbar-nav-label">${item.label}</span>
      </a>`
  ).join("");
}

export function renderBottomNavLinks(items) {
  return items
    .filter((item) => item.includeInBottomNav !== false)
    .map(
      (item) => `
        <a href="#${item.path}" class="mobile-bottom-nav-item" data-path="${item.path}">
          <span class="material-symbols-outlined">${item.mobileIcon || "circle"}</span>
          <span class="mobile-bottom-nav-label">${item.label}</span>
        </a>`
    ).join("");
}

export function renderTopBar({
  variant = "app",
  organizationName = null,
  centerHtml = "",
  rightHtml = "",
} = {}) {
  const config = TOP_BAR_VARIANTS[variant] ?? TOP_BAR_VARIANTS.app;
  const topBarBody = `
    <div class="${config.leftClass}">
      ${renderBrand({
        ...config.brand,
        text: "PeopleMesh",
        organizationName,
        organizationSeparator: "| ",
      })}
    </div>
    ${config.centerClass
    ? `<div class="${config.centerClass}">
      ${centerHtml}
    </div>`
    : ""}
    <div class="${config.rightClass}">
      ${rightHtml}
    </div>
  `;

  const inner = config.containerClass
    ? `<div class="${config.containerClass}">${topBarBody}</div>`
    : topBarBody;

  return `<${config.wrapperTag} class="${config.wrapperClass}"${config.wrapperAttrs}>${inner}</${config.wrapperTag}>`;
}
