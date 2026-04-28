import { describe, it, expect } from "vitest";
import { renderBrandElement, renderBrand } from "../assets/js/brand.js";

describe("renderBrandElement()", () => {
  it("returns a wrapper span element", () => {
    const element = renderBrandElement();
    expect(element.tagName).toBe("SPAN");
  });

  it("contains an anchor by default", () => {
    const element = renderBrandElement();
    const link = element.querySelector("a");
    expect(link).not.toBeNull();
    expect(link.getAttribute("href")).toBe("#/");
  });

  it("sets custom href", () => {
    const element = renderBrandElement({ href: "#/dashboard" });
    const link = element.querySelector("a");
    expect(link.getAttribute("href")).toBe("#/dashboard");
  });

  it("uses custom wrapperTag", () => {
    const element = renderBrandElement({ wrapperTag: "div" });
    const div = element.querySelector("div");
    expect(div).not.toBeNull();
    expect(div.querySelector("a")).toBeNull();
  });

  it("sets aria-label when provided", () => {
    const element = renderBrandElement({ ariaLabel: "Home" });
    const link = element.querySelector("a");
    expect(link.getAttribute("aria-label")).toBe("Home");
  });

  it("contains the brand text", () => {
    const element = renderBrandElement();
    expect(element.textContent).toContain("PeopleMesh");
  });

  it("uses custom text", () => {
    const element = renderBrandElement({ text: "MyApp" });
    expect(element.textContent).toContain("MyApp");
  });

  it("applies className to the link", () => {
    const element = renderBrandElement({ className: "brand-link" });
    const link = element.querySelector("a");
    expect(link.className).toBe("brand-link");
  });

  it("contains the network-wired icon", () => {
    const element = renderBrandElement({ iconClass: "brand-icon" });
    const icon = element.querySelector(".brand-icon");
    expect(icon).not.toBeNull();
    const i = icon.querySelector("i");
    expect(i.className).toContain("fa-network-wired");
  });

  it("renders organization text outside the link", () => {
    const element = renderBrandElement({
      organizationName: "Acme Corp",
      organizationClass: "brand-organization",
    });
    const link = element.querySelector("a");
    const organization = element.querySelector(".brand-organization");
    expect(link).not.toBeNull();
    expect(organization).not.toBeNull();
    expect(organization.textContent).toBe("| Acme Corp");
    expect(link.textContent).not.toContain("Acme Corp");
  });

  it("does not render organization text when value is blank", () => {
    const element = renderBrandElement({
      organizationName: "   ",
      organizationClass: "brand-organization",
    });
    expect(element.querySelector(".brand-organization")).toBeNull();
  });
});

describe("renderBrand()", () => {
  it("returns an HTML string", () => {
    const html = renderBrand();
    expect(typeof html).toBe("string");
    expect(html).toContain("PeopleMesh");
  });

  it("contains an anchor tag", () => {
    const html = renderBrand();
    expect(html).toContain("<a");
    expect(html).toContain("href=");
  });
});
