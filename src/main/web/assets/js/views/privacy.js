import { Auth } from "../auth.js";
import { el, toast, modal, spinner } from "../ui.js";
import { getUserFacingErrorMessage } from "../utils/errors.js";
import {
  deleteMyAccount,
  exportMyData,
  grantMyConsent,
  listMyConsents,
  revokeMyConsent,
} from "../services/privacy-service.js";

export async function renderPrivacy(container) {
  container.dataset.page = "privacy";
  container.innerHTML = "";

  /* === Header === */
  const header = el("header", { className: "priv-header" });
  header.appendChild(el("h1", { className: "page-title" }, "Privacy Dashboard"));
  header.appendChild(el("p", { className: "page-subtitle text-secondary" }, "GDPR compliant data control and transparency."));
  container.appendChild(header);

  /* === 2-col, 3-row grid === */
  const grid = el("div", { className: "priv-grid" });

  /* === Row 1, full width: Privacy Status === */
  const statusCard = el("div", { className: "priv-card priv-card--full" });
  const statusHeader = el("div", { className: "priv-card-header" });
  statusHeader.appendChild(el("h2", { className: "priv-card-title" },
    el("span", { className: "material-symbols-outlined priv-icon priv-icon--blue" }, "shield"),
    "Privacy Status"
  ));
  statusCard.appendChild(statusHeader);
  const statusBody = el("div", { className: "priv-card-body" });
  statusBody.appendChild(el("p", { className: "priv-status-desc" },
    "Your data is access-controlled and only shared according to your choices and active consents."
  ));
  const statusBullets = el("div", { className: "priv-status-bullets" });
  statusBullets.appendChild(statusBullet("lock", "Access-controlled data with TLS in transit and infrastructure-level encryption at rest"));
  statusBody.appendChild(statusBullets);
  statusCard.appendChild(statusBody);
  grid.appendChild(statusCard);

  /* === Row 2, col 1: Export Data + Delete Account stacked === */
  const actionsCol = el("div", { className: "priv-actions-col" });

  const exportCard = el("div", { className: "priv-card" });
  const exportHeader = el("div", { className: "priv-card-header" });
  exportHeader.appendChild(el("h2", { className: "priv-card-title" },
    el("span", { className: "material-symbols-outlined priv-icon priv-icon--blue" }, "download"),
    "Export Data"
  ));
  exportCard.appendChild(exportHeader);
  const exportBody = el("div", { className: "priv-card-body" });
  exportBody.appendChild(el("p", { className: "text-sm text-secondary priv-card-copy" },
    "Download all your personal data as a JSON file (GDPR Art. 20)."
  ));
  exportBody.appendChild(el("button", {
    className: "btn btn-secondary priv-card-action-btn",
    onClick: exportData,
  },
    el("span", { className: "material-symbols-outlined icon-18" }, "download"),
    el("span", {}, "Download My Data")
  ));
  exportCard.appendChild(exportBody);
  actionsCol.appendChild(exportCard);

  const deleteCard = el("div", { className: "priv-card priv-card--danger" });
  const deleteHeader = el("div", { className: "priv-card-header" });
  deleteHeader.appendChild(el("h2", { className: "priv-card-title priv-card-title--danger" },
    el("span", { className: "material-symbols-outlined icon-20" }, "warning"),
    "Delete Account"
  ));
  deleteCard.appendChild(deleteHeader);
  const deleteBody = el("div", { className: "priv-card-body" });
  deleteBody.appendChild(el("p", { className: "text-sm text-secondary priv-card-copy" },
    "Permanently delete your account and all associated data. This action cannot be undone."
  ));
  deleteBody.appendChild(el("button", {
    className: "btn btn-danger priv-card-action-btn",
    onClick: deleteAccount,
  },
    el("span", { className: "material-symbols-outlined icon-18" }, "delete_forever"),
    el("span", {}, "Delete My Account")
  ));
  deleteCard.appendChild(deleteBody);
  actionsCol.appendChild(deleteCard);

  grid.appendChild(actionsCol);

  /* === Row 2, col 2: Consent Management === */
  const consentCard = el("div", { className: "priv-card" });
  const consentHeader = el("div", { className: "priv-card-header" });
  consentHeader.appendChild(el("h2", { className: "priv-card-title" },
    el("span", { className: "material-symbols-outlined priv-icon priv-icon--blue" }, "policy"),
    "Consent Management"
  ));
  consentCard.appendChild(consentHeader);
  const consentBody = el("div", { className: "priv-card-body" });
  consentBody.appendChild(el("p", { className: "priv-consent-desc text-secondary" },
    "Control which processing activities you consent to. Revoking a consent may limit certain features."
  ));
  const consentGrid = el("div", { className: "priv-consent-grid" });
  consentGrid.appendChild(spinner());
  consentBody.appendChild(consentGrid);
  consentCard.appendChild(consentBody);
  grid.appendChild(consentCard);

  container.appendChild(grid);

  /* === Load data === */
  await loadConsents(consentGrid);
}

/* === Status bullet === */

function statusBullet(icon, text) {
  return el("div", { className: "priv-status-bullet" },
    el("span", { className: "material-symbols-outlined priv-status-bullet-icon" }, icon),
    el("span", {}, text)
  );
}

/* === Data export === */

async function exportData() {
  try {
    const data = await exportMyData();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "peoplemesh-data-export.json";
    a.click();
    URL.revokeObjectURL(url);
    toast("Data exported successfully", "success");
  } catch (err) { toast(getUserFacingErrorMessage(err, "Could not export data."), "error"); }
}

/* === Account deletion === */

async function deleteAccount() {
  modal("Delete Account",
    el("p", { className: "text-error" },
      "This action is irreversible. All your data will be permanently deleted immediately. Are you absolutely sure?"
    ), {
      actions: [{
        label: "Delete My Account",
        className: "btn-danger",
        onClick: async () => {
          try {
            await deleteMyAccount();
            toast("Account deletion initiated", "info");
            await Auth.logout();
          } catch (err) { toast(getUserFacingErrorMessage(err, "Could not delete account."), "error"); }
        },
      }],
    });
}

/* === Consent management === */

const SCOPE_LABELS = {
  professional_matching: {
    label: "Professional Matching",
    desc: "Match your profile with people and opportunities.",
    icon: "group",
    warning: "You will no longer appear in search results and won\u2019t be able to find matches.",
  },
  embedding_processing: {
    label: "Embedding Processing",
    desc: "Generate semantic embeddings for smarter matching.",
    icon: "psychology",
    warning: "New profile changes won\u2019t generate embeddings, reducing match quality.",
  },
};

async function loadConsents(consentGrid) {
  try {
    const data = await listMyConsents();
    const allScopes = data.scopes || [];
    const active = new Set(data.active || []);

    consentGrid.innerHTML = "";

    for (const scope of allScopes) {
      const meta = SCOPE_LABELS[scope] || { label: scope, desc: "", icon: "help", warning: "" };
      const isActive = active.has(scope);

      const card = el("div", { className: "priv-consent-card" });

      const iconEl = el("span", {
        className: `material-symbols-outlined priv-consent-icon ${isActive ? "priv-consent-icon--active" : "priv-consent-icon--inactive"}`,
      }, meta.icon);
      card.appendChild(iconEl);

      const center = el("div", { className: "priv-consent-center" });
      center.appendChild(el("div", { className: "priv-consent-label" }, meta.label));
      center.appendChild(el("div", { className: "priv-consent-scope-desc text-secondary" }, meta.desc));
      if (meta.warning) {
        const warningClass = isActive ? "priv-consent-warning text-secondary" : "priv-consent-warning priv-consent-warning--active text-secondary";
        const warningIcon = isActive ? "info" : "warning";
        const warningText = isActive ? `If revoked: ${meta.warning}` : meta.warning;
        center.appendChild(el("div", { className: warningClass },
          el("span", { className: "material-symbols-outlined icon-14 inline-icon" }, warningIcon),
          warningText
        ));
      }
      card.appendChild(center);

      const toggle = el("button", {
        className: `priv-consent-toggle${isActive ? " active" : ""}`,
        "aria-label": `Toggle ${meta.label}`,
      }, el("span", { className: "priv-consent-toggle-knob" }));
      toggle.addEventListener("click", async () => {
        if (isActive) {
          modal(`Revoke ${meta.label}`,
            el("div", {},
              el("p", {}, meta.warning),
              el("p", { className: "text-secondary mt-3" },
                "You can re-enable this consent at any time."
              )
            ), {
              actions: [{
                label: "Revoke Consent",
                className: "btn-danger",
                onClick: async () => {
                  try {
                    await revokeMyConsent(scope);
                    toast(`${meta.label} consent revoked`, "info");
                    await loadConsents(consentGrid);
                  } catch (err) { toast(getUserFacingErrorMessage(err, "Could not revoke consent."), "error"); }
                },
              }],
            });
          return;
        }
        toggle.disabled = true;
        try {
          await grantMyConsent(scope);
          toast(`${meta.label} consent granted`, "success");
          await loadConsents(consentGrid);
        } catch (err) {
          toast(getUserFacingErrorMessage(err, "Could not grant consent."), "error");
        } finally {
          toggle.disabled = false;
        }
      });
      card.appendChild(toggle);
      consentGrid.appendChild(card);
    }

    if (allScopes.length === 0) {
      consentGrid.appendChild(el("p", { className: "text-sm text-secondary" }, "No consent scopes available."));
    }
  } catch (err) {
    consentGrid.innerHTML = "";
    consentGrid.appendChild(el("p", { className: "text-sm text-secondary" }, "Could not load consent settings."));
  }
}
