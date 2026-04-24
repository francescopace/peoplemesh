import { Router } from "./router.js";
import { Auth } from "./auth.js";
import { renderLanding } from "./views/landing.js";
import { renderSearch } from "./views/search.js";
import { renderEvents } from "./views/events.js";
import { renderProfile } from "./views/profile.js";
import { renderJobDetail } from "./views/jobs.js";
import { renderPrivacy } from "./views/privacy.js";
import { renderPrivacyPolicy } from "./views/privacy-policy.js";
import { renderTermsOfService } from "./views/terms-of-service.js";
import { renderExplore } from "./views/explore.js";
import { renderPublicProfile } from "./views/public-profile.js";
import { renderAdmin } from "./views/admin.js";
import { renderOAuthImport } from "./views/oauth-import.js";
import { renderAppShell, updateNavActive } from "./app-shell.js";
import { renderRouteFallback } from "./utils/route-fallback.js";
import { getPlatformInfo } from "./platform-info.js";

const appRoot = document.getElementById("app");
const router = new Router(appRoot);

await Auth.init();

getPlatformInfo().then((info) => {
  if (info.organizationName) {
    document.title = `PeopleMesh | ${info.organizationName}`;
  }
});

if (Auth.isAuthenticated()) {
  const pending = sessionStorage.getItem("pm_pending_search");
  if (pending) {
    sessionStorage.removeItem("pm_pending_search");
    window.location.hash = `#/search?q=${encodeURIComponent(pending)}`;
  }
}

function withShell(viewFn) {
  return async (container, params) => {
    document.body.classList.add("app-auth");
    await renderAppShell(container);
    const main = container.querySelector(".app-main");
    if (main) await viewFn(main, params);
    updateNavActive(router.currentPath());
  };
}

router.beforeEach(async (route) => {
  if (route.auth && !Auth.isAuthenticated()) {
    router.navigate("/");
    return false;
  }
  return true;
});

router.on("/", async (container, params) => {
  document.body.classList.remove("app-auth");
  return renderLanding(container, params);
});
router.on("/search", withShell(renderSearch), { auth: true, keepShell: true });
router.on("/profile", withShell(renderProfile), { auth: true, keepShell: true });
router.on("/events", withShell(renderEvents), { auth: true, keepShell: true });
router.on("/my-mesh", withShell(renderExplore), { auth: true, keepShell: true });
router.on("/jobs/:id", withShell(renderJobDetail), { auth: true, keepShell: true });
router.on("/people/:id", withShell(renderPublicProfile), { auth: true, keepShell: true });
router.on("/admin", withShell(renderAdmin), { auth: true, keepShell: true });
router.on("/skills", () => router.navigate("/admin"), { auth: true });
router.on("/privacy", withShell(renderPrivacy), { auth: true, keepShell: true });
router.on("/privacy_policy", renderPrivacyPolicy);
router.on("/terms_of_service", renderTermsOfService);
router.on("/oauth/import", renderOAuthImport);

router.notFound((container) => {
  renderRouteFallback(container, "Page not found");
});

router.start();
