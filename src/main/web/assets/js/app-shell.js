import { Auth } from "./auth.js";
import { renderFooter } from "./footer.js";
import { getOrganizationName } from "./platform-info.js";
import { bindTopBarUserMenu, renderTopBarUserMenu } from "./top-bar-user-menu.js";
import { getAuthenticatedTopBarNavItems, renderBottomNavLinks, renderTopBar, renderTopBarNavLinks } from "./top-bar.js";

export async function renderAppShell(container) {
  if (container.querySelector(".app-layout")) {
    const main = container.querySelector(".app-main");
    if (main) main.innerHTML = "";
    return;
  }

  const user = Auth.getUser();
  const organizationName = await getOrganizationName();
  const topBarUserMenuHtml = await renderTopBarUserMenu();
  const navItems = getAuthenticatedTopBarNavItems(user);
  const topBarCenterHtml = renderTopBarNavLinks(navItems);

  container.innerHTML = `
    <div class="app-layout">
      ${renderTopBar({
        variant: "app",
        organizationName,
        centerHtml: topBarCenterHtml,
        rightHtml: topBarUserMenuHtml,
      })}
      <main class="app-main"></main>
      <nav class="mobile-bottom-nav" aria-label="Main navigation">
        ${renderBottomNavLinks(navItems)}
      </nav>
      ${renderFooter({ extraClass: "app-footer" })}
    </div>
  `;

  bindTopBarUserMenu(container);
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
