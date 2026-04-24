import { Auth } from "./auth.js";
import { renderBrand } from "./brand.js";
import { renderFooter } from "./footer.js";
import { el } from "./ui.js";
import { getMyProfileInFlight } from "./services/profile-service.js";
import { deriveInitials } from "./utils/initials.js";

let _dropdownCloseHandler = null;

const NAV_ITEMS = [
  { path: "/search",     icon: "search",            label: "Search" },
  { path: "/my-mesh",    icon: "hub",               label: "My Mesh" },
];

export async function renderAppShell(container) {
  if (container.querySelector(".app-layout")) {
    const main = container.querySelector(".app-main");
    if (main) main.innerHTML = "";
    return;
  }

  const user = Auth.getUser();
  const initials = deriveInitials(user);

  function escAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  let avatarHtml;
  try {
    const profile = await getMyProfileInFlight().catch(() => null);
    const photoUrl = profile?.identity?.photo_url;
    if (photoUrl && /^https?:\/\//i.test(photoUrl)) {
      avatarHtml = `<img class="user-avatar user-avatar--img" src="${escAttr(photoUrl)}" alt="${escAttr(initials)}" referrerpolicy="no-referrer">`;
    }
  } catch { /* ignore */ }
  if (!avatarHtml) {
    avatarHtml = `<div class="user-avatar">${escAttr(initials)}</div>`;
  }

  container.innerHTML = `
    <div class="app-layout">
      <header class="app-header">
        <div class="app-header-left">
          ${renderBrand({
            className: "app-brand-link",
            ariaLabel: "Go to PeopleMesh landing",
            iconClass: "app-brand-icon",
            textClass: "app-brand-text",
          })}
        </div>
        <div class="app-header-center">
          ${NAV_ITEMS.map(
            (item) => `
            <a href="#${item.path}" class="header-nav-link" data-path="${item.path}">
              ${item.label}
            </a>`
          ).join("")}
        </div>
        <div class="app-header-right">
          <div class="profile-dropdown-wrap" id="profile-dropdown-wrap">
            <button class="user-chip" id="profile-menu-btn" aria-label="Profile menu" aria-haspopup="true" aria-expanded="false">
              ${avatarHtml}
            </button>
            <div class="profile-dropdown" id="profile-dropdown">
              <a href="#/profile" class="profile-dropdown-item">
                <span class="material-symbols-outlined">person</span>
                Profile
              </a>
              <a href="#/events" class="profile-dropdown-item">
                <span class="material-symbols-outlined">history</span>
                Events
              </a>
              <a href="#/privacy" class="profile-dropdown-item">
                <span class="material-symbols-outlined">shield_with_heart</span>
                Privacy
              </a>
              <div class="profile-dropdown-divider"></div>
              <button class="profile-dropdown-item" id="dropdown-logout-btn">
                <span class="material-symbols-outlined">logout</span>
                Sign Out
              </button>
            </div>
          </div>
        </div>
      </header>
      <main class="app-main"></main>
      <nav class="mobile-bottom-nav" aria-label="Main navigation">
        ${NAV_ITEMS.map(
          (item) => `
          <a href="#${item.path}" class="mobile-bottom-nav-item" data-path="${item.path}">
            <span class="material-symbols-outlined">${item.icon}</span>
            <span class="mobile-bottom-nav-label">${item.label}</span>
          </a>`
        ).join("")}
      </nav>
      ${renderFooter({ extraClass: "app-footer" })}
    </div>
  `;

  container.querySelector("#dropdown-logout-btn").addEventListener("click", () => Auth.logout());

  if (user?.entitlements?.is_admin) {
    const navCenter = container.querySelector(".app-header-center");
    if (navCenter) {
      const adminLink = document.createElement("a");
      adminLink.href = "#/admin";
      adminLink.className = "header-nav-link";
      adminLink.dataset.path = "/admin";
      adminLink.textContent = "Admin";
      navCenter.appendChild(adminLink);
    }
  }

  const profileBtn = container.querySelector("#profile-menu-btn");
  const profileDropdown = container.querySelector("#profile-dropdown");

  profileBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    const open = profileDropdown.classList.toggle("open");
    profileBtn.setAttribute("aria-expanded", String(open));
  });

  if (_dropdownCloseHandler) {
    document.removeEventListener("click", _dropdownCloseHandler);
  }
  _dropdownCloseHandler = (e) => {
    if (!e.target.closest("#profile-dropdown-wrap")) {
      profileDropdown.classList.remove("open");
      profileBtn.setAttribute("aria-expanded", "false");
    }
  };
  document.addEventListener("click", _dropdownCloseHandler);

  profileDropdown.addEventListener("click", () => {
    profileDropdown.classList.remove("open");
    profileBtn.setAttribute("aria-expanded", "false");
  });
}

export function updateNavActive(currentPath) {
  const basePath = currentPath.split("?")[0];
  document.querySelectorAll(".header-nav-link, .mobile-bottom-nav-item").forEach((link) => {
    const linkPath = link.dataset.path;
    let isActive = basePath === linkPath || basePath.startsWith(linkPath + "/");
    if (linkPath === "/my-mesh" && (currentPath.startsWith("/jobs/") || currentPath === "/explore")) {
      isActive = true;
    }
    link.classList.toggle("active", isActive);
  });
}
