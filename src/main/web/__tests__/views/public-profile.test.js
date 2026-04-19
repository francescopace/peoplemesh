import { beforeEach, describe, expect, it, vi } from "vitest";

let renderPublicProfile;
let nodesServiceMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("public-profile view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/people/u-1";

    nodesServiceMock = {
      getNodeProfile: vi.fn(),
      getNodeSkills: vi.fn(),
    };

    vi.doMock("../../assets/js/services/nodes-service.js", () => nodesServiceMock);

    const mod = await import("../../assets/js/views/public-profile.js");
    renderPublicProfile = mod.renderPublicProfile;
  });

  it("renders profile header and sections", async () => {
    nodesServiceMock.getNodeProfile.mockResolvedValue({
      identity: { display_name: "Alice", email: "alice@example.com" },
      professional: { roles: ["Engineer"], skills_technical: ["Java"] },
      geography: { country: "IT", city: "Rome" },
      interests_professional: {},
      personal: {},
      field_provenance: {},
    });
    nodesServiceMock.getNodeSkills.mockResolvedValue([]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPublicProfile(container, { id: "u-1" });
    await flushPromises();

    expect(container.textContent).toContain("Alice");
    expect(container.textContent).toContain("Public profile");
    expect(container.textContent).toContain("Professional");
  });

  it("shows 404 fallback when profile is missing", async () => {
    const err = new Error("Not Found");
    err.status = 404;
    nodesServiceMock.getNodeProfile.mockRejectedValue(err);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPublicProfile(container, { id: "u-1" });
    await flushPromises();

    expect(container.textContent).toContain("Profile not found or not public.");
  });
});
