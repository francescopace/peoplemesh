import { beforeEach, describe, expect, it, vi } from "vitest";

let renderJobDetail;
let nodesServiceMock;
let jobsServiceMock;

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe("jobs view", () => {
  beforeEach(async () => {
    vi.resetModules();
    document.body.innerHTML = "";
    window.location.hash = "#/jobs/42";

    nodesServiceMock = {
      getNode: vi.fn(),
    };
    jobsServiceMock = {
      getJobMatches: vi.fn(),
    };

    vi.doMock("../../assets/js/services/nodes-service.js", () => nodesServiceMock);
    vi.doMock("../../assets/js/services/jobs-service.js", () => jobsServiceMock);

    const mod = await import("../../assets/js/views/jobs.js");
    renderJobDetail = mod.renderJobDetail;
  });

  it("renders job details and candidates", async () => {
    nodesServiceMock.getNode.mockResolvedValue({
      id: "42",
      nodeType: "JOB",
      title: "Backend Engineer",
      description: "Build APIs",
      country: "IT",
      tags: ["Java", "SQL"],
      structuredData: { company: "PeopleMesh", location: "Remote" },
      createdAt: "2026-01-01T00:00:00Z",
    });
    jobsServiceMock.getJobMatches.mockResolvedValue([
      {
        id: "u-1",
        title: "Alice",
        score: 0.9,
        person: { roles: ["Engineer"], skillsTechnical: ["Java"] },
      },
    ]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderJobDetail(container, { id: "42" });
    await flushPromises();

    expect(container.textContent).toContain("Backend Engineer");
    expect(container.textContent).toContain("Best Matches");
    expect(container.textContent).toContain("Alice");
  });

  it("shows not-found state for non-job node", async () => {
    nodesServiceMock.getNode.mockResolvedValue({ id: "42", nodeType: "EVENT" });
    jobsServiceMock.getJobMatches.mockResolvedValue([]);

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderJobDetail(container, { id: "42" });
    await flushPromises();

    expect(container.textContent).toContain("Job not found");
  });
});
