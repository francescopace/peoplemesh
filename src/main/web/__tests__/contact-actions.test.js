import { describe, it, expect, vi } from "vitest";
import {
  slackButton,
  copyEmailButton,
  telegramButton,
  mobileButton,
  contactFooter,
} from "../assets/js/contact-actions.js";

describe("slackButton()", () => {
  it("creates a button element", () => {
    const btn = slackButton("alice");
    expect(btn.tagName).toBe("BUTTON");
  });

  it("copies normalized @handle on click", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    const btn = slackButton("alice");
    btn.click();
    expect(writeText).toHaveBeenCalledWith("@alice");

    const btn2 = slackButton("@bob");
    btn2.click();
    expect(writeText).toHaveBeenCalledWith("@bob");
  });

  it("has copy accessibility label", () => {
    const btn = slackButton("eve");
    expect(btn.getAttribute("aria-label")).toBe("Copy Slack handle");
  });
});

describe("copyEmailButton()", () => {
  it("creates a button element", () => {
    const btn = copyEmailButton("test@example.com");
    expect(btn.tagName).toBe("BUTTON");
  });

  it("has accessibility label for email", () => {
    const btn = copyEmailButton("test@example.com");
    expect(btn.getAttribute("aria-label")).toBe("Copy email");
  });

  it("copies email to clipboard on click", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    const btn = copyEmailButton("alice@example.com");
    btn.click();

    expect(writeText).toHaveBeenCalledWith("alice@example.com");
  });
});

describe("telegramButton()", () => {
  it("creates an anchor element", () => {
    const btn = telegramButton("@alice");
    expect(btn.tagName).toBe("A");
  });

  it("sets t.me href and strips @", () => {
    const btn = telegramButton("@alice");
    expect(btn.getAttribute("href")).toBe("https://t.me/alice");
  });
});

describe("mobileButton()", () => {
  it("creates an anchor element", () => {
    const btn = mobileButton("+39 123 456");
    expect(btn.tagName).toBe("A");
  });

  it("sets tel href with normalized whitespace", () => {
    const btn = mobileButton("+39 123 456");
    expect(btn.getAttribute("href")).toBe("tel:+39123456");
  });
});

describe("contactFooter()", () => {
  it("returns element with slack copy button when handle provided", () => {
    const footer = contactFooter("alice", null, null, null);
    expect(footer).not.toBeNull();
    expect(footer.querySelector("button[aria-label='Copy Slack handle']")).not.toBeNull();
  });

  it("returns element with email button when email provided", () => {
    const footer = contactFooter(null, "test@test.com", null, null);
    expect(footer).not.toBeNull();
    expect(footer.querySelector("button")).not.toBeNull();
  });

  it("returns element with all contact actions when provided", () => {
    const footer = contactFooter("alice", "alice@test.com", "@alice_tg", "+39 123 456");
    expect(footer.children.length).toBe(4);
  });

  it("returns null when neither provided", () => {
    const footer = contactFooter(null, null, null, null);
    expect(footer).toBeNull();
  });

  it("returns null for empty strings", () => {
    const footer = contactFooter("", "", "", "");
    expect(footer).toBeNull();
  });

  it("ignores whitespace-only contact values", () => {
    const footer = contactFooter("   ", "\n\t", "  ", " ");
    expect(footer).toBeNull();
  });

  it("ignores literal null/undefined string values", () => {
    const footer = contactFooter("null", "undefined", " NULL ", " Undefined ");
    expect(footer).toBeNull();
  });
});
