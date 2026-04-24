import { beforeEach, describe, expect, it, vi } from "vitest";

let renderPublicProfile;
let nodesServiceMock;
let toastMock;

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
    toastMock = vi.fn();

    vi.doMock("../../assets/js/services/nodes-service.js", () => nodesServiceMock);
    vi.doMock("../../assets/js/ui.js", async () => {
      const actual = await vi.importActual("../../assets/js/ui.js");
      return {
        ...actual,
        toast: toastMock,
      };
    });

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

  it("shows generic error toast on non-404 failures", async () => {
    const err = Object.assign(new Error("Boom"), { status: 500 });
    nodesServiceMock.getNodeProfile.mockRejectedValue(err);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPublicProfile(container, { id: "u-1" });
    await flushPromises();

    expect(container.textContent).toContain("Could not load profile.");
    expect(toastMock).toHaveBeenCalled();
  });

  it("renders identity links, personal sections and grouped skills", async () => {
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    });
    nodesServiceMock.getNodeProfile.mockResolvedValue({
      identity: {
        display_name: "Alice",
        email: "alice@example.com",
        photo_url: "https://cdn.example/avatar.png",
        company: "PeopleMesh",
        birth_date: "1991-02-03",
      },
      professional: {
        roles: ["Engineer"],
        seniority: "SENIOR",
        skills_technical: ["Java"],
        skills_soft: ["Communication"],
        tools_and_tech: ["Kubernetes"],
        industries: ["Software"],
        work_mode_preference: "REMOTE",
        employment_type: "EMPLOYED",
        languages_spoken: ["English"],
      },
      contacts: {
        slack_handle: "@alice",
        linkedin_url: "linkedin.com/in/alice",
      },
      geography: { country: "IT", city: "Rome", timezone: "Europe/Rome" },
      interests_professional: { learning_areas: ["Graph DB"], project_types: ["Platform"] },
      personal: {
        hobbies: ["Hiking"],
        sports: ["Cycling"],
        causes: ["Climate"],
        music_genres: ["Jazz"],
        book_genres: ["Sci-fi"],
        personality_tags: ["Curious"],
        education: ["MSc CS"],
      },
      field_provenance: {
        "identity.photo_url": "github",
        "identity.email": "github",
      },
    });
    nodesServiceMock.getNodeSkills.mockResolvedValue([
      { skill_name: "Java", category: "Technical", level: 4 },
      { skill_name: "SQL", category: "Technical", level: 0 },
      { skill_name: "Mentoring", category: "Soft", level: 3 },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPublicProfile(container, { id: "u-1" });
    await flushPromises();

    expect(container.querySelector(".profile-avatar-img")).not.toBeNull();
    expect(container.textContent).toContain("PeopleMesh");
    expect(container.textContent).toContain("Birth Date");
    expect(container.querySelector('a[href^="slack://user"]')).not.toBeNull();
    const linkedIn = container.querySelector('a[href*="linkedin.com/in/alice"]');
    expect(linkedIn).not.toBeNull();
    expect(linkedIn.getAttribute("href")).toBe("https://linkedin.com/in/alice");
    expect(container.textContent).toContain("Interests & Personal");
    expect(container.textContent).toContain("Hobbies & Sports");
    expect(container.textContent).toContain("Technical");
    expect(container.textContent).toContain("Soft");
    expect(container.textContent).toContain("Lv4");

    container.querySelector('button[title="Copy email"]').click();
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("alice@example.com");
  });

  it("skips skills card when skills call fails", async () => {
    nodesServiceMock.getNodeProfile.mockResolvedValue({
      identity: { display_name: "Alice" },
      professional: { roles: ["Engineer"] },
      geography: {},
      interests_professional: {},
      personal: {},
      field_provenance: {},
    });
    nodesServiceMock.getNodeSkills.mockRejectedValue(new Error("network"));

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderPublicProfile(container, { id: "u-1" });
    await flushPromises();

    expect(container.textContent).not.toContain("Skills");
  });
});
