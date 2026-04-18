import { el, toast } from "./ui.js";

function normalizeContactValue(value) {
  if (value == null) return null;
  const normalized = String(value).trim();
  if (!normalized) return null;
  if (normalized.toLowerCase() === "null") return null;
  if (normalized.toLowerCase() === "undefined") return null;
  return normalized;
}

export function slackButton(slackHandle) {
  const handle = slackHandle.replace(/^@/, "").trim();
  return el("a", {
    className: "btn btn-sm btn-secondary",
    href: `slack://user?team=&id=${handle}`,
    target: "_blank",
    rel: "noopener",
    title: "Contact on Slack",
    "aria-label": "Contact on Slack",
  },
    el("i", { className: "fa-brands fa-slack", style: "font-size:14px;color:#611f69" })
  );
}

export function copyEmailButton(email) {
  return el("button", {
    className: "btn btn-sm btn-secondary",
    title: "Copy email",
    "aria-label": "Copy email",
    onClick: () => {
      navigator.clipboard.writeText(email).catch(() => {});
      toast("Email copied: " + email);
    },
  },
    el("i", { className: "fa-solid fa-envelope", style: "font-size:14px" })
  );
}

export function telegramButton(telegramHandle) {
  const handle = telegramHandle.replace(/^@/, "").trim();
  return el("a", {
    className: "btn btn-sm btn-secondary",
    href: `https://t.me/${handle}`,
    target: "_blank",
    rel: "noopener",
    title: "Contact on Telegram",
    "aria-label": "Contact on Telegram",
  },
    el("i", { className: "fa-brands fa-telegram", style: "font-size:14px;color:#229ED9" })
  );
}

export function mobileButton(mobilePhone) {
  const normalizedPhone = mobilePhone.replace(/\s+/g, "");
  return el("a", {
    className: "btn btn-sm btn-secondary",
    href: `tel:${normalizedPhone}`,
    title: "Call mobile number",
    "aria-label": "Call mobile number",
  },
    el("i", { className: "fa-solid fa-phone", style: "font-size:14px" })
  );
}

export function contactFooter(slackHandle, email, telegramHandle, mobilePhone) {
  const normalizedSlack = normalizeContactValue(slackHandle);
  const normalizedEmail = normalizeContactValue(email);
  const normalizedTelegram = normalizeContactValue(telegramHandle);
  const normalizedMobile = normalizeContactValue(mobilePhone);

  const actions = el("div", { className: "dc-footer" });
  if (normalizedSlack) actions.appendChild(slackButton(normalizedSlack));
  if (normalizedEmail) actions.appendChild(copyEmailButton(normalizedEmail));
  if (normalizedTelegram) actions.appendChild(telegramButton(normalizedTelegram));
  if (normalizedMobile) actions.appendChild(mobileButton(normalizedMobile));
  return actions.children.length ? actions : null;
}
