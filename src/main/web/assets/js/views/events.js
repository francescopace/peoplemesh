import { api } from "../api.js";
import { el, spinner, emptyState, toast, pageHeader } from "../ui.js";

export async function renderEvents(container) {
  container.dataset.page = "events";
  container.innerHTML = "";

  const root = el("div", { className: "events-page stack-8" });
  root.appendChild(pageHeader("Events", "Activity timeline and notifications."));

  const content = el("div", { className: "events-content" });
  content.appendChild(spinner());
  root.appendChild(content);
  container.appendChild(root);

  try {
    const [notifications, privacyActivity] = await Promise.all([
      api.get("/api/v1/me/notifications", { limit: 50 }).catch(() => []),
      api.get("/api/v1/me/activity").catch(() => null),
    ]);

    content.innerHTML = "";

    const events = buildEventList(notifications, privacyActivity);

    if (events.length === 0) {
      content.appendChild(emptyState("No recent events."));
      return;
    }

    const list = el("div", { className: "events-list" });
    events.forEach((evt) => list.appendChild(eventRow(evt)));
    content.appendChild(list);
  } catch (err) {
    content.innerHTML = "";
    content.appendChild(emptyState("Unable to load events."));
    toast(err.message, "error");
  }
}

function buildEventList(notifications, privacyActivity) {
  const events = [];

  if (privacyActivity?.lastProfileUpdate) {
    events.push({
      icon: "person",
      title: "Profile updated",
      detail: "Profile changes have been saved.",
      timestamp: privacyActivity.lastProfileUpdate,
      status: "SUCCESS",
    });
  }

  if (privacyActivity?.activeConsents > 0) {
    events.push({
      icon: "policy",
      title: `Active consents: ${privacyActivity.activeConsents}`,
      detail: "Data processing consents are active.",
      timestamp: new Date().toISOString(),
      status: "ACTIVE",
    });
  }

  const HIDDEN_ACTIONS = new Set([
    "NODE_MATCHES_FOUND",
    "JOBS_MATCHED",
    "CANDIDATES_MATCHED_FOR_JOB",
  ]);

  if (Array.isArray(notifications)) {
    notifications.filter((item) => !HIDDEN_ACTIONS.has(item.action)).forEach((item) => {
      events.push({
        icon: iconForAction(item.action),
        title: item.subject || humanizeAction(item.action),
        detail: item.toolName ? `Tool: ${item.toolName}` : "",
        timestamp: item.timestamp,
        status: "INFO",
        metadata: item.metadata,
      });
    });
  }

  events.sort((a, b) => {
    const ta = a.timestamp ? new Date(a.timestamp).getTime() : 0;
    const tb = b.timestamp ? new Date(b.timestamp).getTime() : 0;
    return tb - ta;
  });

  return events;
}

function eventRow(evt) {
  const row = el("div", { className: "event-row" });

  const iconWrap = el("div", { className: `event-icon event-icon--${statusColor(evt.status)}` });
  iconWrap.appendChild(el("span", { className: "material-symbols-outlined" }, evt.icon));
  row.appendChild(iconWrap);

  const body = el("div", { className: "event-body" });
  body.appendChild(el("div", { className: "event-title" }, evt.title));
  if (evt.detail) {
    body.appendChild(el("div", { className: "event-detail text-secondary" }, evt.detail));
  }
  if (evt.metadata) {
    body.appendChild(el("pre", { className: "event-metadata" },
      typeof evt.metadata === "string" ? evt.metadata : JSON.stringify(evt.metadata, null, 2)));
  }
  row.appendChild(body);

  const right = el("div", { className: "event-right" });
  right.appendChild(el("time", { className: "event-time text-secondary" }, formatDate(evt.timestamp)));
  const badge = el("span", { className: `event-badge event-badge--${statusColor(evt.status)}` }, evt.status);
  right.appendChild(badge);
  row.appendChild(right);

  return row;
}

function statusColor(status) {
  switch (status) {
    case "SUCCESS":
    case "ACTIVE": return "green";
    case "PENDING": return "amber";
    case "FAILED": return "red";
    default: return "blue";
  }
}

function iconForAction(action) {
  if (!action) return "notifications";
  const a = action.toLowerCase();
  if (a.includes("profile")) return "person";
  if (a.includes("consent")) return "policy";
  if (a.includes("match")) return "hub";
  if (a.includes("search")) return "search";
  if (a.includes("login") || a.includes("auth")) return "lock";
  if (a.includes("export")) return "download";
  if (a.includes("delete")) return "delete";
  return "notifications";
}

function humanizeAction(action) {
  if (!action) return "Event";
  return action.toLowerCase().replaceAll("_", " ");
}

function formatDate(timestamp) {
  if (!timestamp) return "\u2014";
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return "\u2014";
  return date.toLocaleString("en-US");
}
