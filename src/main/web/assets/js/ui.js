export function el(tag, attrs = {}, ...children) {
  const element = document.createElement(tag);
  for (const [key, val] of Object.entries(attrs)) {
    if (key === "className") {
      element.className = val;
    } else if (key === "dataset") {
      Object.assign(element.dataset, val);
    } else if (key.startsWith("on") && typeof val === "function") {
      element.addEventListener(key.slice(2).toLowerCase(), val);
    } else {
      element.setAttribute(key, val);
    }
  }
  for (const child of children) {
    if (child == null) continue;
    if (typeof child === "string" || typeof child === "number") {
      element.appendChild(document.createTextNode(String(child)));
    } else {
      element.appendChild(child);
    }
  }
  return element;
}

export function badge(text, variant = "default") {
  return el("span", { className: `badge badge-${variant}` }, text);
}

export function table(columns, rows, { emptyText = "No data" } = {}) {
  const t = el("div", { className: "table-wrapper" });
  const tbl = el("table", { className: "data-table" });

  const thead = el("tr", {});
  columns.forEach((col) =>
    thead.appendChild(el("th", {}, typeof col === "string" ? col : col.label))
  );
  tbl.appendChild(el("thead", {}, thead));

  const tbody = el("tbody", {});
  if (rows.length === 0) {
    const emptyRow = el("tr", {});
    emptyRow.appendChild(
      el("td", { colspan: String(columns.length), className: "empty-cell" }, emptyText)
    );
    tbody.appendChild(emptyRow);
  } else {
    rows.forEach((row) => {
      const tr = el("tr", {});
      columns.forEach((col) => {
        const key = typeof col === "string" ? col : col.key;
        const render = typeof col === "object" && col.render;
        const td = el("td", {});
        if (render) {
          const content = render(row[key], row);
          if (content instanceof HTMLElement) td.appendChild(content);
          else td.textContent = content ?? "";
        } else {
          td.textContent = row[key] ?? "";
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
  }
  tbl.appendChild(tbody);
  t.appendChild(tbl);
  return t;
}

export function spinner() {
  return el("div", { className: "spinner", role: "status", "aria-label": "Loading" });
}

export function emptyState(message, actionLabel, actionFn) {
  const wrapper = el("div", { className: "empty-state" }, el("p", {}, message));
  if (actionLabel && actionFn) {
    wrapper.appendChild(el("button", { className: "btn btn-primary", onClick: actionFn }, actionLabel));
  }
  return wrapper;
}

export function modal(title, contentEl, { onClose, actions = [], dialogClassName = "", headerActions = [] } = {}) {
  const overlay = el("div", { className: "modal-overlay", onClick: (e) => { if (e.target === overlay) close(); } });
  const dialogClass = dialogClassName ? `modal-dialog ${dialogClassName}` : "modal-dialog";
  const dialog = el("div", { className: dialogClass, role: "dialog", "aria-modal": "true", "aria-label": title });
  const header = el("div", { className: "modal-header" });
  header.appendChild(el("h3", {}, title));
  const headerRight = el("div", { className: "modal-header-actions" });
  if (headerActions instanceof HTMLElement) {
    headerRight.appendChild(headerActions);
  } else if (Array.isArray(headerActions)) {
    headerActions.forEach((actionEl) => {
      if (actionEl instanceof HTMLElement) headerRight.appendChild(actionEl);
    });
  }
  headerRight.appendChild(el("button", { className: "modal-close btn btn-ghost", "aria-label": "Close", onClick: () => close() }, "\u00d7"));
  header.appendChild(headerRight);
  const body = el("div", { className: "modal-body" });
  if (contentEl instanceof HTMLElement) body.appendChild(contentEl);
  else body.appendChild(document.createTextNode(String(contentEl)));

  dialog.appendChild(header);
  dialog.appendChild(body);

  if (actions.length) {
    const footer = el("div", { className: "modal-footer" });
    actions.forEach((a) =>
      footer.appendChild(
        el("button", { className: `btn ${a.className || "btn-secondary"}`, onClick: () => { a.onClick(); close(); } }, a.label)
      )
    );
    dialog.appendChild(footer);
  }

  overlay.appendChild(dialog);
  document.body.appendChild(overlay);

  function close() {
    overlay.remove();
    if (onClose) onClose();
  }

  return { close };
}

let _toastContainer = null;

export function toast(message, variant = "info", duration = 4000) {
  if (!_toastContainer) {
    _toastContainer = el("div", { className: "toast-container", "aria-live": "polite" });
    document.body.appendChild(_toastContainer);
  }
  const t = el("div", { className: `toast toast-${variant}` }, message);
  _toastContainer.appendChild(t);
  requestAnimationFrame(() => t.classList.add("toast-visible"));
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    t.classList.remove("toast-visible");
    t.addEventListener("transitionend", () => t.remove(), { once: true });
    setTimeout(() => t.remove(), 300);
  };
  if (typeof duration === "number" && duration > 0) {
    setTimeout(() => {
      close();
    }, duration);
  }
  return { close };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function toastForPromise(promiseOrFactory, {
  loadingMessage = "Working...",
  successMessage = null,
  errorMessage = (err) => err?.message || "Request failed",
  minVisibleMs = 800,
  successDuration = 3000,
  errorDuration = 5000,
} = {}) {
  const startedAt = Date.now();
  const loadingToast = toast(loadingMessage, "info", 0);
  try {
    const promise = typeof promiseOrFactory === "function" ? promiseOrFactory() : promiseOrFactory;
    const result = await promise;
    const elapsedMs = Date.now() - startedAt;
    if (elapsedMs < minVisibleMs) await sleep(minVisibleMs - elapsedMs);
    loadingToast.close();
    if (successMessage) {
      const msg = typeof successMessage === "function" ? successMessage(result) : successMessage;
      if (msg) toast(msg, "success", successDuration);
    }
    return result;
  } catch (err) {
    const elapsedMs = Date.now() - startedAt;
    if (elapsedMs < minVisibleMs) await sleep(minVisibleMs - elapsedMs);
    loadingToast.close();
    const msg = typeof errorMessage === "function" ? errorMessage(err) : errorMessage;
    if (msg) toast(msg, "error", errorDuration);
    throw err;
  }
}

export function pageHeader(title, subtitle, actions) {
  const header = el("div", { className: "page-header" });
  const text = el("div", {});
  text.appendChild(el("h1", { className: "page-title" }, title));
  if (subtitle) text.appendChild(el("p", { className: "page-subtitle text-secondary" }, subtitle));
  header.appendChild(text);
  if (actions) {
    const actionsEl = el("div", { className: "page-actions flex gap-3" });
    if (actions instanceof HTMLElement) actionsEl.appendChild(actions);
    else actions.forEach((a) => actionsEl.appendChild(a));
    header.appendChild(actionsEl);
  }
  return header;
}
