import { el, spinner } from "../ui.js";
import { finalizeImportFromOAuthCallback } from "../services/auth-service.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";

function getHashParams() {
  const hashQuery = window.location.hash.split("?")[1] || "";
  return new URLSearchParams(hashQuery);
}

function postImportMessage(message) {
  if (!window.opener) return;
  window.opener.postMessage(message, window.location.origin);
}

export async function renderOAuthImport(container) {
  document.body.classList.remove("app-auth");
  container.innerHTML = "";

  const status = el("p", { className: "oauth-import-status" }, "Preparing import...");
  const card = el("div", { className: "oauth-import-card" },
    spinner(),
    el("h1", { className: "oauth-import-title" }, "Importing from GitHub"),
    el("p", { className: "oauth-import-subtitle" }, "Processing repositories and profile signals. This usually takes a few seconds."),
    status
  );
  container.appendChild(el("section", { className: "oauth-import-page", "aria-live": "polite" }, card));

  const params = getHashParams();
  const provider = String(params.get("provider") || "").trim();
  const code = String(params.get("code") || "").trim();
  const state = String(params.get("state") || "").trim();
  const callbackError = String(params.get("error") || "").trim();

  if (!provider) {
    status.textContent = "Import failed. Missing provider.";
    postImportMessage({ type: "import-error", error: "Import failed" });
    setTimeout(() => window.close(), 3000);
    return;
  }

  if (callbackError) {
    status.textContent = "Import failed. This window will close.";
    postImportMessage({ type: "import-error", error: callbackError });
    setTimeout(() => window.close(), 3000);
    return;
  }

  if (!code || !state) {
    status.textContent = "Import failed. Missing OAuth parameters.";
    postImportMessage({ type: "import-error", error: "Missing code or state" });
    setTimeout(() => window.close(), 3000);
    return;
  }

  try {
    const result = await finalizeImportFromOAuthCallback(provider, {
      code,
      state,
    });
    postImportMessage({
      type: "import-result",
      imported: result?.imported || null,
      source: result?.source || provider,
    });
    window.close();
  } catch (err) {
    status.textContent = "Import failed. This window will close.";
    postImportMessage({
      type: "import-error",
      error: getUserFacingErrorMessage(err, "Import failed"),
    });
    setTimeout(() => window.close(), 3000);
  }
}
