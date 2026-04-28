import { Config } from "../../config.js";

class ApiClient {
  constructor() {
    this._onUnauthorized = null;
  }

  setUnauthorizedHandler(handler) {
    this._onUnauthorized = typeof handler === "function" ? handler : null;
  }

  async request(method, path, { body, query, headers: customHeaders } = {}) {
    let url = `${Config.apiBase}${path}`;
    if (query) {
      const params = new URLSearchParams();
      for (const [k, v] of Object.entries(query)) {
        if (v != null && v !== "") params.set(k, v);
      }
      const qs = params.toString();
      if (qs) url += "?" + qs;
    }

    const headers = { Accept: "application/json", "X-Requested-With": "XMLHttpRequest" };
    if (customHeaders && typeof customHeaders === "object") {
      Object.assign(headers, customHeaders);
    }

    const opts = { method, headers };
    if (body != null) {
      if (body instanceof FormData) {
        opts.body = body;
      } else if (body instanceof ArrayBuffer || body instanceof Blob) {
        if (!headers["Content-Type"]) headers["Content-Type"] = "application/octet-stream";
        opts.body = body;
      } else {
        if (!headers["Content-Type"]) headers["Content-Type"] = "application/json";
        opts.body = JSON.stringify(body);
      }
    }

    const res = await fetch(url, opts);

    if (res.status === 401) {
      if (this._onUnauthorized) {
        try {
          this._onUnauthorized({ method, path, status: 401 });
        } catch {
          // Ignore callback errors: caller still receives unauthorized error.
        }
      }
      throw new Error("Unauthorized");
    }

    if (res.status === 204) return null;

    const data = await res.json().catch(() => null);

    if (!res.ok) {
      const detail =
        data?.detail || data?.message || `Request failed (${res.status})`;
      const err = new Error(detail);
      err.status = res.status;
      err.body = data;
      throw err;
    }

    return data;
  }

  get(path, query) {
    return this.request("GET", path, { query });
  }
  post(path, body, options = {}) {
    return this.request("POST", path, { ...options, body });
  }
  put(path, body, options = {}) {
    return this.request("PUT", path, { ...options, body });
  }
  patch(path, body, options = {}) {
    return this.request("PATCH", path, { ...options, body });
  }
  delete(path) {
    return this.request("DELETE", path);
  }
}

export const api = new ApiClient();
