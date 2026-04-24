import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("events-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = { get: vi.fn() };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/events-service.js");
  });

  it("loads notifications with limit and activity", async () => {
    await service.listMyNotifications(25);
    await service.getMyActivity();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me/notifications", { limit: 25 });
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/me/activity");
  });
});
