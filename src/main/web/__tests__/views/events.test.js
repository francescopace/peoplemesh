import { beforeEach, describe, expect, it, vi } from "vitest";

let renderEvents;
let eventsServiceMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("events view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/events";

    eventsServiceMock = {
      listMyNotifications: vi.fn(),
      getMyActivity: vi.fn(),
    };

    vi.doMock("../../assets/js/services/events-service.js", () => eventsServiceMock);

    const mod = await import("../../assets/js/views/events.js");
    renderEvents = mod.renderEvents;
  });

  it("renders merged events list and hides noisy match actions", async () => {
    eventsServiceMock.listMyNotifications.mockResolvedValue([
      { action: "PROFILE_UPDATED", subject: "Profile synced", timestamp: "2026-01-01T10:00:00Z" },
      { action: "NODE_MATCHES_FOUND", subject: "Hidden", timestamp: "2026-01-01T11:00:00Z" },
    ]);
    eventsServiceMock.getMyActivity.mockResolvedValue({
      lastProfileUpdate: "2026-01-01T09:00:00Z",
      activeConsents: 2,
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderEvents(container);
    await flushPromises();

    expect(container.textContent).toContain("Profile synced");
    expect(container.textContent).toContain("Active consents: 2");
    expect(container.textContent).not.toContain("Hidden");
  });

  it("shows empty fallback when both sources fail softly", async () => {
    eventsServiceMock.listMyNotifications.mockRejectedValue(new Error("oops"));
    eventsServiceMock.getMyActivity.mockRejectedValue(new Error("oops"));

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderEvents(container);
    await flushPromises();

    expect(container.textContent).toContain("No recent events.");
  });
});
