import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("admin-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = { get: vi.fn() };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/admin-service.js");
  });

  it("loads system statistics", async () => {
    await service.getSystemStatistics();
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/system/statistics");
  });
});
