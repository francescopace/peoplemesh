import { beforeEach, describe, expect, it, vi } from "vitest";

let service;
let apiMock;

describe("jobs-service", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = { get: vi.fn() };
    vi.doMock("../../assets/js/api.js", () => ({ api: apiMock }));
    service = await import("../../assets/js/services/jobs-service.js");
  });

  it("loads job matches for people", async () => {
    await service.getJobMatches("job-1");
    expect(apiMock.get).toHaveBeenCalledWith("/api/v1/matches/job-1", { type: "PEOPLE" });
  });
});
