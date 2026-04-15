import { describe, it, expect, vi, beforeEach } from "vitest";
import { Router } from "../assets/js/router.js";

describe("Router", () => {
  let container;
  let router;

  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    window.location.hash = "";
    router = new Router(container);
  });

  describe("on()", () => {
    it("registers routes and returns the router for chaining", () => {
      const result = router.on("/home", vi.fn());
      expect(result).toBe(router);
    });
  });

  describe("notFound()", () => {
    it("sets a notFound handler and returns the router", () => {
      const result = router.notFound(vi.fn());
      expect(result).toBe(router);
    });
  });

  describe("beforeEach()", () => {
    it("sets a guard and returns the router", () => {
      const result = router.beforeEach(vi.fn());
      expect(result).toBe(router);
    });
  });

  describe("navigate()", () => {
    it("sets window.location.hash", () => {
      router.navigate("/search");
      expect(window.location.hash).toBe("#/search");
    });
  });

  describe("currentPath()", () => {
    it("returns / when hash is empty", () => {
      window.location.hash = "";
      expect(router.currentPath()).toBe("/");
    });

    it("returns the path after #", () => {
      window.location.hash = "#/profile";
      expect(router.currentPath()).toBe("/profile");
    });
  });

  describe("_resolve()", () => {
    it("calls the matching handler with container and params", async () => {
      const handler = vi.fn();
      router.on("/users/:id", handler);
      window.location.hash = "#/users/123";

      await router._resolve();

      expect(handler).toHaveBeenCalledWith(container, { id: "123" });
    });

    it("clears container when route does not have keepShell", async () => {
      container.innerHTML = "<p>old</p>";
      router.on("/clean", vi.fn());
      window.location.hash = "#/clean";

      await router._resolve();

      expect(container.innerHTML).not.toContain("old");
    });

    it("preserves container when keepShell is true", async () => {
      container.innerHTML = "<p>keep</p>";
      router.on("/keep", vi.fn(), { keepShell: true });
      window.location.hash = "#/keep";

      await router._resolve();

      expect(container.innerHTML).toContain("keep");
    });

    it("calls notFound when no route matches", async () => {
      const notFound = vi.fn();
      router.notFound(notFound);
      window.location.hash = "#/unknown";

      await router._resolve();

      expect(notFound).toHaveBeenCalledWith(container);
    });

    it("calls beforeEach guard before handler", async () => {
      const order = [];
      router.beforeEach(async () => {
        order.push("guard");
        return true;
      });
      router.on("/guarded", () => order.push("handler"));
      window.location.hash = "#/guarded";

      await router._resolve();

      expect(order).toEqual(["guard", "handler"]);
    });

    it("blocks navigation when beforeEach returns false", async () => {
      const handler = vi.fn();
      router.beforeEach(async () => false);
      router.on("/blocked", handler);
      window.location.hash = "#/blocked";

      await router._resolve();

      expect(handler).not.toHaveBeenCalled();
    });

    it("sets and removes aria-busy during handler execution", async () => {
      let busyDuringHandler = false;
      router.on("/busy", () => {
        busyDuringHandler = container.getAttribute("aria-busy") === "true";
      });
      window.location.hash = "#/busy";

      await router._resolve();

      expect(busyDuringHandler).toBe(true);
      expect(container.hasAttribute("aria-busy")).toBe(false);
    });

    it("decodes URI-encoded params", async () => {
      const handler = vi.fn();
      router.on("/tag/:name", handler);
      window.location.hash = "#/tag/hello%20world";

      await router._resolve();

      expect(handler).toHaveBeenCalledWith(container, { name: "hello world" });
    });

    it("strips query string before matching", async () => {
      const handler = vi.fn();
      router.on("/search", handler);
      window.location.hash = "#/search?q=test";

      await router._resolve();

      expect(handler).toHaveBeenCalledWith(container, {});
    });

    it("matches exact patterns only", async () => {
      const homeHandler = vi.fn();
      const homeSubHandler = vi.fn();
      router.on("/home", homeHandler);
      router.on("/home/sub", homeSubHandler);

      window.location.hash = "#/home/sub";
      await router._resolve();

      expect(homeHandler).not.toHaveBeenCalled();
      expect(homeSubHandler).toHaveBeenCalled();
    });
  });

  describe("start()", () => {
    it("sets default hash to #/ when empty", () => {
      window.location.hash = "";
      const handler = vi.fn();
      router.on("/", handler);
      router.start();
      expect(window.location.hash).toBe("#/");
    });
  });
});
