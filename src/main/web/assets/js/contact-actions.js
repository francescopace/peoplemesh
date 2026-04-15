import { el, toast } from "./ui.js";

export function slackButton(slackHandle) {
  const handle = slackHandle.replace(/^@/, "");
  return el("a", {
    className: "btn btn-sm btn-secondary",
    href: `slack://user?team=&id=${handle}`,
    target: "_blank",
    rel: "noopener",
    title: "Contact on Slack",
  },
    el("i", { className: "fa-brands fa-slack", style: "font-size:14px;color:#611f69" }),
    " Slack"
  );
}

export function copyEmailButton(email) {
  return el("button", {
    className: "btn btn-sm btn-secondary",
    title: "Copy email",
    onClick: () => {
      navigator.clipboard.writeText(email).catch(() => {});
      toast("Email copied: " + email);
    },
  },
    el("span", { className: "material-symbols-outlined" }, "mail"),
    " Email"
  );
}

export function contactFooter(slackHandle, email) {
  const actions = el("div", { className: "dc-footer" });
  if (slackHandle) actions.appendChild(slackButton(slackHandle));
  if (email) actions.appendChild(copyEmailButton(email));
  return actions.children.length ? actions : null;
}
