export class Router {
  constructor(container) {
    this._container = container;
    this._routes = [];
    this._notFound = null;
    this._beforeEach = null;
    this._navGeneration = 0;
    window.addEventListener("hashchange", () => this._resolve());
  }

  on(pattern, handler, { auth = false, keepShell = false } = {}) {
    const keys = [];
    const regex = new RegExp(
      "^" +
        pattern.replace(/:([^/]+)/g, (_, key) => {
          keys.push(key);
          return "([^/]+)";
        }) +
        "$"
    );
    this._routes.push({ regex, keys, handler, auth, keepShell });
    return this;
  }

  notFound(handler) {
    this._notFound = handler;
    return this;
  }

  beforeEach(fn) {
    this._beforeEach = fn;
    return this;
  }

  start() {
    if (!window.location.hash) {
      window.location.hash = "#/";
    }
    this._resolve();
  }

  navigate(path) {
    window.location.hash = "#" + path;
  }

  currentPath() {
    return window.location.hash.slice(1) || "/";
  }

  async _resolve() {
    const navId = ++this._navGeneration;
    const path = this.currentPath().split("?")[0];

    if (!path.startsWith("/")) {
      const target = document.getElementById(path);
      if (target) target.scrollIntoView({ behavior: "smooth" });
      return;
    }

    for (const route of this._routes) {
      const match = path.match(route.regex);
      if (!match) continue;

      if (this._beforeEach) {
        const allowed = await this._beforeEach(route, path);
        if (navId !== this._navGeneration) return;
        if (!allowed) return;
      }

      const params = {};
      try {
        route.keys.forEach((key, i) => {
          params[key] = decodeURIComponent(match[i + 1]);
        });
      } catch {
        if (this._notFound) {
          this._container.innerHTML = "";
          this._notFound(this._container);
        }
        return;
      }

      if (!route.keepShell) {
        this._container.innerHTML = "";
      }
      this._container.setAttribute("aria-busy", "true");

      try {
        await route.handler(this._container, params);
      } catch {
        if (navId === this._navGeneration) {
          this._container.innerHTML = `
            <div class="empty-state" style="min-height:100dvh">
              <p>Something went wrong while loading this page.</p>
              <a href="#/" class="btn btn-primary">Go Home</a>
            </div>`;
        }
      } finally {
        if (navId === this._navGeneration) {
          this._container.removeAttribute("aria-busy");
        }
      }
      return;
    }

    if (this._notFound) {
      this._container.innerHTML = "";
      this._notFound(this._container);
    }
  }
}
