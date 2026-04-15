import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { formatTimeAgo } from "../../assets/js/utils/time.js";

describe("formatTimeAgo", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-04-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns em-dash for null/undefined", () => {
    expect(formatTimeAgo(null)).toBe("\u2014");
    expect(formatTimeAgo(undefined)).toBe("\u2014");
  });

  it("returns em-dash for empty string", () => {
    expect(formatTimeAgo("")).toBe("\u2014");
  });

  it("returns em-dash for invalid date string", () => {
    expect(formatTimeAgo("not-a-date")).toBe("\u2014");
  });

  it("returns 'just now' for less than 1 minute ago", () => {
    const now = new Date("2026-04-15T11:59:45Z").toISOString();
    expect(formatTimeAgo(now)).toBe("just now");
  });

  it("returns minutes ago for < 60 min", () => {
    const thirtyMinAgo = new Date("2026-04-15T11:30:00Z").toISOString();
    expect(formatTimeAgo(thirtyMinAgo)).toBe("30m ago");
  });

  it("returns hours ago for < 24 hours", () => {
    const fiveHoursAgo = new Date("2026-04-15T07:00:00Z").toISOString();
    expect(formatTimeAgo(fiveHoursAgo)).toBe("5h ago");
  });

  it("returns days ago for >= 24 hours", () => {
    const threeDaysAgo = new Date("2026-04-12T12:00:00Z").toISOString();
    expect(formatTimeAgo(threeDaysAgo)).toBe("3d ago");
  });

  it("handles numeric timestamp (epoch ms)", () => {
    const oneHourAgo = Date.now() - 3600 * 1000;
    expect(formatTimeAgo(oneHourAgo)).toBe("1h ago");
  });

  it("returns 1m ago for exactly 60 seconds", () => {
    const oneMinAgo = new Date("2026-04-15T11:59:00Z").toISOString();
    expect(formatTimeAgo(oneMinAgo)).toBe("1m ago");
  });
});
