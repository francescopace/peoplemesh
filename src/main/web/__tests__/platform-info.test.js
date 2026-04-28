import { beforeEach, describe, expect, it, vi } from "vitest";

let getPlatformInfo;
let getOrganizationName;
let apiMock;

describe("platform-info", () => {
  beforeEach(async () => {
    vi.resetModules();
    apiMock = {
      get: vi.fn(),
    };
    vi.doMock("../assets/js/api.js", () => ({ api: apiMock }));
    ({ getPlatformInfo, getOrganizationName } = await import("../assets/js/platform-info.js"));
  });

  it("deduplicates concurrent loads and reuses cached value", async () => {
    apiMock.get.mockResolvedValue({ organizationName: "Acme Corp" });

    const [first, second] = await Promise.all([getPlatformInfo(), getPlatformInfo()]);
    const third = await getPlatformInfo();

    expect(apiMock.get).toHaveBeenCalledTimes(1);
    expect(first).toEqual({ organizationName: "Acme Corp" });
    expect(second).toEqual({ organizationName: "Acme Corp" });
    expect(third).toEqual({ organizationName: "Acme Corp" });
  });

  it("caches empty object after fetch error", async () => {
    apiMock.get.mockRejectedValue(new Error("network error"));

    const [first, second] = await Promise.all([getPlatformInfo(), getPlatformInfo()]);
    const third = await getPlatformInfo();

    expect(apiMock.get).toHaveBeenCalledTimes(1);
    expect(first).toEqual({});
    expect(second).toEqual({});
    expect(third).toEqual({});
  });

  it("returns normalized organization name", async () => {
    apiMock.get.mockResolvedValue({ organizationName: "  Acme Corp  " });
    const organizationName = await getOrganizationName();
    expect(organizationName).toBe("Acme Corp");
  });

  it("returns null when organization name is missing", async () => {
    apiMock.get.mockResolvedValue({});
    const organizationName = await getOrganizationName();
    expect(organizationName).toBeNull();
  });
});
