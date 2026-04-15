import { describe, it, expect, vi } from "vitest";
import { el, badge, spinner, emptyState, pageHeader, table } from "../assets/js/ui.js";

describe("el()", () => {
  it("creates an element with the given tag", () => {
    const div = el("div");
    expect(div.tagName).toBe("DIV");
  });

  it("sets className attribute", () => {
    const span = el("span", { className: "foo bar" });
    expect(span.className).toBe("foo bar");
  });

  it("sets arbitrary attributes", () => {
    const input = el("input", { type: "text", placeholder: "Name" });
    expect(input.getAttribute("type")).toBe("text");
    expect(input.getAttribute("placeholder")).toBe("Name");
  });

  it("sets dataset properties", () => {
    const div = el("div", { dataset: { userId: "42", role: "admin" } });
    expect(div.dataset.userId).toBe("42");
    expect(div.dataset.role).toBe("admin");
  });

  it("adds event listeners for on* attributes", () => {
    const handler = vi.fn();
    const btn = el("button", { onClick: handler });
    btn.click();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("appends string children as text nodes", () => {
    const p = el("p", {}, "Hello ", "World");
    expect(p.textContent).toBe("Hello World");
  });

  it("appends number children as text nodes", () => {
    const span = el("span", {}, 42);
    expect(span.textContent).toBe("42");
  });

  it("appends element children", () => {
    const child = el("span", {}, "inner");
    const parent = el("div", {}, child);
    expect(parent.children.length).toBe(1);
    expect(parent.children[0].textContent).toBe("inner");
  });

  it("skips null and undefined children", () => {
    const div = el("div", {}, null, "text", undefined);
    expect(div.childNodes.length).toBe(1);
    expect(div.textContent).toBe("text");
  });

  it("handles mixed children", () => {
    const div = el("div", {}, "text", el("span", {}, "child"), 99);
    expect(div.childNodes.length).toBe(3);
  });
});

describe("badge()", () => {
  it("creates a span with badge class", () => {
    const b = badge("New");
    expect(b.tagName).toBe("SPAN");
    expect(b.className).toBe("badge badge-default");
    expect(b.textContent).toBe("New");
  });

  it("applies variant class", () => {
    const b = badge("Active", "success");
    expect(b.className).toBe("badge badge-success");
  });
});

describe("spinner()", () => {
  it("creates a div with spinner class and role", () => {
    const s = spinner();
    expect(s.tagName).toBe("DIV");
    expect(s.className).toBe("spinner");
    expect(s.getAttribute("role")).toBe("status");
    expect(s.getAttribute("aria-label")).toBe("Loading");
  });
});

describe("emptyState()", () => {
  it("creates a wrapper with message", () => {
    const es = emptyState("No results");
    expect(es.className).toBe("empty-state");
    expect(es.textContent).toContain("No results");
  });

  it("adds an action button when provided", () => {
    const action = vi.fn();
    const es = emptyState("Empty", "Add", action);
    const btn = es.querySelector("button");
    expect(btn).not.toBeNull();
    expect(btn.textContent).toBe("Add");
    btn.click();
    expect(action).toHaveBeenCalled();
  });

  it("omits button when no action provided", () => {
    const es = emptyState("Empty");
    expect(es.querySelector("button")).toBeNull();
  });
});

describe("pageHeader()", () => {
  it("creates header with title", () => {
    const h = pageHeader("Dashboard");
    expect(h.querySelector("h1").textContent).toBe("Dashboard");
  });

  it("includes subtitle when provided", () => {
    const h = pageHeader("Dashboard", "Welcome back");
    expect(h.querySelector("p").textContent).toBe("Welcome back");
  });

  it("omits subtitle paragraph when not provided", () => {
    const h = pageHeader("Dashboard");
    expect(h.querySelector("p")).toBeNull();
  });

  it("appends action elements", () => {
    const btn = el("button", {}, "Action");
    const h = pageHeader("Title", null, [btn]);
    expect(h.querySelector("button").textContent).toBe("Action");
  });

  it("appends a single HTMLElement as action", () => {
    const btn = el("button", {}, "Single");
    const h = pageHeader("Title", null, btn);
    expect(h.querySelector("button").textContent).toBe("Single");
  });
});

describe("table()", () => {
  it("renders column headers", () => {
    const t = table(["Name", "Age"], []);
    const ths = t.querySelectorAll("th");
    expect(ths.length).toBe(2);
    expect(ths[0].textContent).toBe("Name");
    expect(ths[1].textContent).toBe("Age");
  });

  it("renders rows with string column keys", () => {
    const t = table(["name"], [{ name: "Alice" }, { name: "Bob" }]);
    const tds = t.querySelectorAll("td");
    expect(tds.length).toBe(2);
    expect(tds[0].textContent).toBe("Alice");
    expect(tds[1].textContent).toBe("Bob");
  });

  it("shows empty text when no rows", () => {
    const t = table(["col"], []);
    expect(t.textContent).toContain("No data");
  });

  it("uses custom emptyText", () => {
    const t = table(["col"], [], { emptyText: "Nothing here" });
    expect(t.textContent).toContain("Nothing here");
  });

  it("uses object column with label and key", () => {
    const t = table(
      [{ label: "Full Name", key: "name" }],
      [{ name: "Carol" }]
    );
    expect(t.querySelector("th").textContent).toBe("Full Name");
    expect(t.querySelector("td").textContent).toBe("Carol");
  });

  it("uses custom render function", () => {
    const t = table(
      [{ label: "Name", key: "name", render: (val) => val.toUpperCase() }],
      [{ name: "dave" }]
    );
    expect(t.querySelector("td").textContent).toBe("DAVE");
  });

  it("render function returning HTMLElement is appended", () => {
    const t = table(
      [{
        label: "Link",
        key: "url",
        render: (val) => el("a", { href: val }, "click"),
      }],
      [{ url: "https://example.com" }]
    );
    const link = t.querySelector("a");
    expect(link).not.toBeNull();
    expect(link.textContent).toBe("click");
  });

  it("handles null values in cells", () => {
    const t = table(["name"], [{ name: null }]);
    expect(t.querySelector("td").textContent).toBe("");
  });
});
