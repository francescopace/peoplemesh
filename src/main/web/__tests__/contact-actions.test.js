import { describe, it, expect, vi } from "vitest";
import { slackButton, copyEmailButton, contactFooter } from "../assets/js/contact-actions.js";

describe("slackButton()", () => {
  it("creates an anchor element", () => {
    const btn = slackButton("alice");
    expect(btn.tagName).toBe("A");
  });

  it("sets slack:// protocol href", () => {
    const btn = slackButton("alice");
    expect(btn.getAttribute("href")).toContain("slack://user");
    expect(btn.getAttribute("href")).toContain("alice");
  });

  it("strips leading @ from handle", () => {
    const btn = slackButton("@bob");
    expect(btn.getAttribute("href")).toContain("bob");
    expect(btn.getAttribute("href")).not.toContain("@");
  });

  it("has noopener rel attribute", () => {
    const btn = slackButton("charlie");
    expect(btn.getAttribute("rel")).toBe("noopener");
  });

  it("opens in new tab", () => {
    const btn = slackButton("dave");
    expect(btn.getAttribute("target")).toBe("_blank");
  });

  it("contains Slack text", () => {
    const btn = slackButton("eve");
    expect(btn.textContent).toContain("Slack");
  });
});

describe("copyEmailButton()", () => {
  it("creates a button element", () => {
    const btn = copyEmailButton("test@example.com");
    expect(btn.tagName).toBe("BUTTON");
  });

  it("contains Email text", () => {
    const btn = copyEmailButton("test@example.com");
    expect(btn.textContent).toContain("Email");
  });

  it("copies email to clipboard on click", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    const btn = copyEmailButton("alice@example.com");
    btn.click();

    expect(writeText).toHaveBeenCalledWith("alice@example.com");
  });
});

describe("contactFooter()", () => {
  it("returns element with slack button when handle provided", () => {
    const footer = contactFooter("alice", null);
    expect(footer).not.toBeNull();
    expect(footer.querySelector("a")).not.toBeNull();
  });

  it("returns element with email button when email provided", () => {
    const footer = contactFooter(null, "test@test.com");
    expect(footer).not.toBeNull();
    expect(footer.querySelector("button")).not.toBeNull();
  });

  it("returns element with both when both provided", () => {
    const footer = contactFooter("alice", "alice@test.com");
    expect(footer.children.length).toBe(2);
  });

  it("returns null when neither provided", () => {
    const footer = contactFooter(null, null);
    expect(footer).toBeNull();
  });

  it("returns null for empty strings", () => {
    const footer = contactFooter("", "");
    expect(footer).toBeNull();
  });
});
