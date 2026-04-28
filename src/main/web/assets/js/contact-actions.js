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
  const display = handle ? `@${handle}` : String(slackHandle || "").trim();
  return el("button", {
    type: "button",
    className: "btn btn-sm btn-secondary",
    title: "Copy Slack handle",
    "aria-label": "Copy Slack handle",
    onClick: () => {
      navigator.clipboard.writeText(display).catch(() => {});
      toast("Slack handle copied");
    },
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
      toast("Email copied");
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

function normalizeLinkedinUrl(value) {
  if (!value) return null;
  const trimmed = String(value).trim();
  if (!trimmed) return null;
  if (/^https?:\/\//i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

export function linkedinButton(linkedinUrl) {
  const normalizedUrl = normalizeLinkedinUrl(linkedinUrl);
  if (!normalizedUrl) return null;
  return el("a", {
    className: "btn btn-sm btn-secondary",
    href: normalizedUrl,
    target: "_blank",
    rel: "noopener",
    title: "Open LinkedIn profile",
    "aria-label": "Open LinkedIn profile",
  },
    el("i", { className: "fa-brands fa-linkedin", style: "font-size:14px;color:#0a66c2" })
  );
}

export function contactFooter(slackHandle, email, telegramHandle, mobilePhone, linkedinUrl) {
  const normalizedSlack = normalizeContactValue(slackHandle);
  const normalizedEmail = normalizeContactValue(email);
  const normalizedTelegram = normalizeContactValue(telegramHandle);
  const normalizedMobile = normalizeContactValue(mobilePhone);
  const normalizedLinkedin = normalizeContactValue(linkedinUrl);

  const actions = el("div", { className: "dc-footer" });
  if (normalizedMobile) actions.appendChild(mobileButton(normalizedMobile));
  if (normalizedSlack) actions.appendChild(slackButton(normalizedSlack));
  if (normalizedTelegram) actions.appendChild(telegramButton(normalizedTelegram));
  if (normalizedLinkedin) {
    const linkedinAction = linkedinButton(normalizedLinkedin);
    if (linkedinAction) actions.appendChild(linkedinAction);
  }
  if (normalizedEmail) actions.appendChild(copyEmailButton(normalizedEmail));
  return actions.children.length ? actions : null;
}
