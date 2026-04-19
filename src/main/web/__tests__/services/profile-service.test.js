import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("profile-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
    };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/profile-service.js");
  });

  it("loads profile and skills endpoints", async () => {
    await service.getMyProfile();
    await service.getMySkills();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me/skills");
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

  it("updates profile and skills", async () => {
    await service.updateMyProfile({ identity: { birth_date: "1991-01-01" } });
    await service.saveMySkills([{ skill_id: "s1", level: 2, interest: false }]);
    expect(apiMock.put).toHaveBeenCalledWith("/api/v1/me", { identity: { birth_date: "1991-01-01" } });
    expect(apiMock.put).toHaveBeenCalledWith("/api/v1/me/skills", [{ skill_id: "s1", level: 2, interest: false }]);
  });
});
