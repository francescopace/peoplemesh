import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("privacy-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
      delete: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/privacy-service.js");
  });

  it("exports data and lists consents", async () => {
    await service.exportMyData();
    await service.listMyConsents();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me/export");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me/consents");
  });

  it("grants and revokes scoped consent", async () => {
    await service.grantMyConsent("embedding_processing");
    await service.revokeMyConsent("embedding_processing");
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/me/consents/embedding_processing");
    expect(apiMock.delete).toHaveBeenCalledWith("/api/v1/me/consents/embedding_processing");
  });

  it("deletes account", async () => {
    await service.deleteMyAccount();
    expect(apiMock.delete).toHaveBeenCalledWith("/api/v1/me");
  });
});
