import { Auth } from "./auth.js";

const PROVIDER_META = {
  google: {
    label: "Google",
    icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18A10.96 10.96 0 0 0 1 12c0 1.77.42 3.45 1.18 4.93l3.66-2.84z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>`,
  },
  microsoft: {
    label: "Microsoft",
    icon: `<svg viewBox="0 0 24 24" width="20" height="20"><rect fill="#F25022" x="1" y="1" width="10.5" height="10.5"/><rect fill="#7FBA00" x="12.5" y="1" width="10.5" height="10.5"/><rect fill="#00A4EF" x="1" y="12.5" width="10.5" height="10.5"/><rect fill="#FFB900" x="12.5" y="12.5" width="10.5" height="10.5"/></svg>`,
  },
};

function escAttr(s) {
  return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function showLoginModal() {
  const existing = document.getElementById("login-modal-overlay");
  if (existing) existing.remove();
  const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  const providers = Auth.getProviders();
  if (providers.length === 1) {
    Auth.login(providers[0]);
    return;
  }

  const overlay = document.createElement("div");
  overlay.id = "login-modal-overlay";
  overlay.className = "login-overlay";

  const buttons = providers
    .map((p) => {
      const meta = PROVIDER_META[p] || { label: p, icon: "" };
      return `
        <button class="login-provider-btn" data-provider="${escAttr(p)}">
          <span class="login-provider-icon">${meta.icon}</span>
          <span>Continue with ${escAttr(meta.label)}</span>
        </button>`;
    })
    .join("");
  const noProvidersNotice = providers.length
    ? ""
    : `<p class="login-empty">No login provider is configured. Set at least one OIDC client ID and secret.</p>`;

  overlay.innerHTML = `
    <div class="login-dialog" role="dialog" aria-modal="true" aria-label="Sign in">
      <button class="login-close" aria-label="Close">&times;</button>
      <div class="login-header">
        <div class="login-logo">PeopleMesh</div>
        <p class="login-subtitle">Sign in to discover your company's mesh.</p>
      </div>
      <div class="login-providers">
        ${buttons}
      </div>
      ${noProvidersNotice}
      <p class="login-terms">
        By continuing, you agree to our
        <a href="#/terms_of_service">Terms of Service</a>
        and
        <a href="#/privacy_policy">Privacy Policy</a>.
      </p>
    </div>
  `;

  document.body.appendChild(overlay);
  requestAnimationFrame(() => overlay.classList.add("login-overlay-visible"));
  const dialog = overlay.querySelector(".login-dialog");
  const firstFocusable = dialog.querySelector(".login-provider-btn, .login-close, a[href], button");
  if (firstFocusable instanceof HTMLElement) {
    firstFocusable.focus();
  }

  overlay.querySelector(".login-close").addEventListener("click", closeModal);
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) closeModal();
  });
  overlay.addEventListener("keydown", onOverlayKeyDown);

  overlay.querySelectorAll(".login-provider-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const provider = btn.dataset.provider;
      btn.classList.add("login-provider-loading");
      btn.disabled = true;
      Auth.login(provider);
    });
  });

  function closeModal() {
    overlay.removeEventListener("keydown", onOverlayKeyDown);
    overlay.classList.remove("login-overlay-visible");
    let removed = false;
    const removeOverlay = () => {
      if (removed) return;
      removed = true;
      overlay.remove();
      if (previouslyFocused) {
        previouslyFocused.focus();
      }
    };
    overlay.addEventListener("transitionend", removeOverlay, { once: true });
    setTimeout(removeOverlay, 250);
  }

  function onOverlayKeyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      closeModal();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = [...dialog.querySelectorAll("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")]
      .filter((node) => !node.hasAttribute("disabled"));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
