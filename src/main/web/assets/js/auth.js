import { api } from "./api.js";
import {
  buildAuthLoginUrl,
  getAuthProviders,
  getCurrentUserIdentityOnly,
  logoutSession,
} from "./services/auth-service.js";
import { Config } from "../../config.js";

const USER_KEY = "pm_user";

class AuthManager {
  _user = null;
  _initialized = false;
  _providers = [];
  _configured = [];
  _isLoggingOut = false;

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
      const user = await getCurrentUserIdentityOnly();
      this.setUser(user);
    } catch {
      this.setUser(null);
    }
    this._initialized = true;
  }

  async refreshProviders() {
    try {
      const data = await getAuthProviders();
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
    window.location.href = buildAuthLoginUrl(provider);
  }

  async logout() {
    if (this._isLoggingOut) return;
    this._isLoggingOut = true;
    try {
      try {
        await logoutSession();
      } catch {
        // Ignore errors on logout
      }
      this.setUser(null);
      window.location.hash = "#/";
    } finally {
      this._isLoggingOut = false;
    }
  }
}

export const Auth = new AuthManager();

api.setUnauthorizedHandler(({ path }) => {
  if (!path || path === "/api/v1/me" || path.startsWith("/api/v1/auth/")) {
    return;
  }
  void Auth.logout();
});