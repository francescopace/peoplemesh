import { describe, expect, it } from "vitest";
import { buildCenteredPopupFeatures } from "../../assets/js/views/profile.js";

function parseFeatures(features) {
  return Object.fromEntries(
    String(features)
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean)
      .map((entry) => {
        const [key, value = ""] = entry.split("=");
        return [key, value];
      })
  );
}

describe("buildCenteredPopupFeatures", () => {
  it("centers popup using current window bounds", () => {
    const features = buildCenteredPopupFeatures(600, 700, {
      screenX: 100,
      screenY: 50,
      innerWidth: 1200,
      innerHeight: 800,
      document: { documentElement: { clientWidth: 0, clientHeight: 0 } },
      screen: { width: 0, height: 0 },
    });
    const parsed = parseFeatures(features);

    expect(parsed.popup).toBe("yes");
    expect(parsed.width).toBe("600");
    expect(parsed.height).toBe("700");
    expect(parsed.left).toBe("400");
    expect(parsed.top).toBe("100");
  });

  it("shrinks popup to 90% of viewport on small screens", () => {
    const features = buildCenteredPopupFeatures(600, 700, {
      screenX: 10,
      screenY: 20,
      innerWidth: 500,
      innerHeight: 600,
      document: { documentElement: { clientWidth: 0, clientHeight: 0 } },
      screen: { width: 0, height: 0 },
    });
    const parsed = parseFeatures(features);

    expect(parsed.width).toBe("450");
    expect(parsed.height).toBe("540");
    expect(parsed.left).toBe("35");
    expect(parsed.top).toBe("50");
  });

  it("clamps negative coordinates to the visible screen", () => {
    const features = buildCenteredPopupFeatures(600, 700, {
      screenX: -500,
      screenY: -300,
      innerWidth: 300,
      innerHeight: 300,
      document: { documentElement: { clientWidth: 0, clientHeight: 0 } },
      screen: { width: 0, height: 0 },
    });
    const parsed = parseFeatures(features);

    expect(parsed.width).toBe("270");
    expect(parsed.height).toBe("270");
    expect(parsed.left).toBe("0");
    expect(parsed.top).toBe("0");
  });
});
