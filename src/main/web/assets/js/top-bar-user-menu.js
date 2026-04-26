import { Auth } from "./auth.js";
import { deriveInitials } from "./utils/initials.js";

let _dropdownCloseHandler = null;

function escAttr(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function renderTopBarUserMenu() {
  const user = Auth.getUser();
  const initials = deriveInitials(user);

  let avatarHtml = `<div class="user-avatar">${escAttr(initials)}</div>`;
  const photoUrl = user?.photo_url;
  if (photoUrl && /^https?:\/\//i.test(photoUrl)) {
    avatarHtml = `<img class="user-avatar user-avatar--img" src="${escAttr(photoUrl)}" alt="${escAttr(initials)}" referrerpolicy="no-referrer">`;
  }

  return `
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
  `;
}

export function bindTopBarUserMenu(container) {
  const profileBtn = container.querySelector("#profile-menu-btn");
  const profileDropdown = container.querySelector("#profile-dropdown");
  const logoutBtn = container.querySelector("#dropdown-logout-btn");
  if (!profileBtn || !profileDropdown || !logoutBtn) return;

  const closeDropdown = () => {
    profileDropdown.classList.remove("open");
    profileBtn.setAttribute("aria-expanded", "false");
  };

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
      closeDropdown();
    }
  };
  document.addEventListener("click", _dropdownCloseHandler);

  profileDropdown.addEventListener("click", closeDropdown);
  logoutBtn.addEventListener("click", () => Auth.logout());
}
