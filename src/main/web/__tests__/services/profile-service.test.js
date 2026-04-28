import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("profile-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/profile-service.js");
  });

  it("loads profile endpoint", async () => {
    await service.getMyProfile();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me");
  });

  it("deduplicates concurrent profile reads through getMyProfileInFlight", async () => {
    let resolveProfile;
    apiMock.get.mockImplementation(() => new Promise((resolve) => {
      resolveProfile = resolve;
    }));
    const firstPromise = service.getMyProfileInFlight();
    const secondPromise = service.getMyProfileInFlight();

    expect(apiMock.get).toHaveBeenCalledTimes(1);
    resolveProfile({ id: "p1" });
    const first = await firstPromise;
    const second = await secondPromise;
    expect(first).toEqual({ id: "p1" });
    expect(second).toEqual({ id: "p1" });
  });

  it("does not cache completed reads for future calls", async () => {
    apiMock.get.mockResolvedValue({ id: "p1" });
    await service.getMyProfileInFlight();
    await service.getMyProfileInFlight();
    expect(apiMock.get).toHaveBeenCalledTimes(2);
  });

  it("imports and applies profile payload", async () => {
    const fd = new FormData();
    await service.importMyCv(fd);
    expect(apiMock.post).toHaveBeenCalledWith("/api/v1/me/cv-import", fd);

    await service.applyImportedProfile("github", { identity: { birth_date: "1990-01-01" } });
    expect(apiMock.post).toHaveBeenCalledWith(
      "/api/v1/me/import-apply?source=github",
      { identity: { birth_date: "1990-01-01" } }
    );
  });

  it("updates profile", async () => {
    await service.updateMyProfile({ identity: { birth_date: "1991-01-01" } });
    expect(apiMock.patch).toHaveBeenCalledWith(
      "/api/v1/me",
      { identity: { birth_date: "1991-01-01" } },
      { headers: { "Content-Type": "application/merge-patch+json" } }
    );
  });

  it("patches profile via merge patch endpoint", async () => {
    await service.patchMyProfile({ identity: { birth_date: null } });
    expect(apiMock.patch).toHaveBeenCalledWith(
      "/api/v1/me",
      { identity: { birth_date: null } },
      { headers: { "Content-Type": "application/merge-patch+json" } }
    );
  });
});
