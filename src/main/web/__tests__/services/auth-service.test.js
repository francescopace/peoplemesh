import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("auth-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/auth-service.js");
  });

  it("loads identity-only me endpoint", async () => {
    await service.getCurrentUserIdentityOnly();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me", { identity_only: true });
  });

  it("posts logout session", async () => {
    await service.logoutSession();
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/auth/logout");
  });

  it("finalizes oauth import callback through api", async () => {
    await service.finalizeImportFromOAuthCallback("github", { code: "abc", state: "xyz" });
    expect(apiMock.get).toHaveBeenCalledWith(
      "/api/v1/auth/callback/github/import-finalize",
      { code: "abc", state: "xyz" }
    );
  });

  it("builds login url with filtered query params", () => {
    const url = service.buildAuthLoginUrl("github", {
      intent: "profile_import",
      empty: "",
      nullable: null,
    });
    expect(url).toContain("/api/v1/auth/login/github");
    expect(url).toContain("intent=profile_import");
    expect(url).not.toContain("empty=");
    expect(url).not.toContain("nullable=");
  });
});
