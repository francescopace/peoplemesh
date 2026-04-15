import { Config } from "../../config.js";
import { api } from "./api.js";

const USER_KEY = "pm_user";

class AuthManager {
  _user = null;
  _initialized = false;
  _providers = [];
  _configured = [];

  constructor() {
    const stored = sessionStorage.getItem(USER_KEY);
    if (stored) {
      try {
        this._user = JSON.parse(stored);
      } catch {
        /* ignore */
      }
    }
  }

  async init() {
    if (this._initialized) return;
    await this.refreshProviders();
    try {
      const user = await api.get("/api/v1/me", { identity_only: true });
      this.setUser(user);
    } catch {
      this.setUser(null);
    }
    this._initialized = true;
  }

  async refreshProviders() {
    try {
      const data = await api.get("/api/v1/auth/providers");
      const login = Array.isArray(data?.providers) ? data.providers : [];
      const configured = Array.isArray(data?.configured) ? data.configured : [];
      this._providers = Config.providers.filter((p) => login.includes(p));
      this._configured = Config.providers.filter((p) => configured.includes(p));
    } catch {
      this._providers = [];
      this._configured = [];
    }
  }

  isAuthenticated() {
    return !!this._user;
  }

  getUser() {
    return this._user;
  }

  setUser(user) {
    this._user = user;
    if (user) {
      sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(USER_KEY);
    }
  }

  getProviders() {
    return this._providers;
  }

  isProviderConfigured(provider) {
    return this._configured.includes(provider);
  }

  login(provider) {
    if (!this._providers.includes(provider) && !this._configured.includes(provider)) {
      return;
    }
    window.location.href = `${Config.apiBase}/api/v1/auth/login/${provider}`;
  }

  async logout() {
    try {
      await fetch(`${Config.apiBase}/api/v1/auth/logout`, {
        method: "POST",
        headers: { "X-Requested-With": "XMLHttpRequest" },
      });
    } catch {
      // Ignore errors on logout
    }
    this.setUser(null);
    window.location.hash = "#/";
  }
}

export const Auth = new AuthManager();