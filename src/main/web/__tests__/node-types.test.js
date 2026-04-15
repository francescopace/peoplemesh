import { describe, it, expect } from "vitest";
import { NODE_TYPE_ICONS, NODE_TYPE_COLORS } from "../assets/js/node-types.js";

describe("NODE_TYPE_ICONS", () => {
  it("defines icons for all known node types", () => {
    expect(NODE_TYPE_ICONS.USER).toBe("person");
    expect(NODE_TYPE_ICONS.JOB).toBe("work");
    expect(NODE_TYPE_ICONS.COMMUNITY).toBe("groups");
    expect(NODE_TYPE_ICONS.EVENT).toBe("event");
    expect(NODE_TYPE_ICONS.PROJECT).toBe("rocket_launch");
    expect(NODE_TYPE_ICONS.INTEREST_GROUP).toBe("interests");
  });

  it("USER and PEOPLE share the same icon", () => {
    expect(NODE_TYPE_ICONS.PEOPLE).toBe(NODE_TYPE_ICONS.USER);
  });
});

describe("NODE_TYPE_COLORS", () => {
  it("defines color objects for all known types", () => {
    const types = ["PEOPLE", "USER", "JOB", "COMMUNITY", "EVENT", "PROJECT", "INTEREST_GROUP"];
    for (const type of types) {
      expect(NODE_TYPE_COLORS[type]).toBeDefined();
      expect(NODE_TYPE_COLORS[type]).toHaveProperty("bg");
      expect(NODE_TYPE_COLORS[type]).toHaveProperty("color");
      expect(NODE_TYPE_COLORS[type]).toHaveProperty("border");
    }
  });

  it("USER and PEOPLE share the same colors", () => {
    expect(NODE_TYPE_COLORS.PEOPLE).toEqual(NODE_TYPE_COLORS.USER);
  });

  it("different types have different primary colors", () => {
    const uniqueColors = new Set(
      Object.values(NODE_TYPE_COLORS).map((c) => c.color)
    );
    expect(uniqueColors.size).toBeGreaterThan(3);
  });
});
