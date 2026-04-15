import { Auth } from "../auth.js";
import { emptyState, pageHeader } from "../ui.js";
import { renderSkillsCatalog } from "./skills.js";

export async function renderAdmin(container) {
  container.dataset.page = "admin";
  container.innerHTML = "";

  const user = Auth.getUser() || {};
  if (!user.entitlements?.can_manage_skills) {
    container.appendChild(emptyState("You do not have permission to access this page."));
    return;
  }

  container.appendChild(pageHeader("Administration", "Manage skill catalogs"));
  renderSkillsCatalog(container);
}
