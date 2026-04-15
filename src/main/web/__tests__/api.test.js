import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

let api;
let authLogoutSpy;

describe("ApiClient", () => {
  beforeEach(async () => {
    vi.resetModules();

    authLogoutSpy = vi.fn();
    vi.doMock("../assets/js/auth.js", () => ({
      Auth: { logout: authLogoutSpy },
    }));
    vi.doMock("../config.js", () => ({
      Config: { apiBase: "https://api.test" },
    }));

    const mod = await import("../assets/js/api.js");
    api = mod.api;

    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("GET sends correct method and headers", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: "ok" }),
    });

    const result = await api.get("/api/v1/test");

    expect(global.fetch).toHaveBeenCalledWith(
      "https://api.test/api/v1/test",
      expect.objectContaining({ method: "GET" })
    );
    expect(result).toEqual({ data: "ok" });
  });

  it("POST sends JSON body", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 1 }),
    });

    await api.post("/api/v1/items", { name: "test" });

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.method).toBe("POST");
    expect(opts.body).toBe('{"name":"test"}');
    expect(opts.headers["Content-Type"]).toBe("application/json");
  });

  it("PUT sends correct method", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await api.put("/api/v1/items/1", { name: "updated" });

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.method).toBe("PUT");
  });

  it("PATCH sends correct method", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await api.patch("/api/v1/items/1", { name: "patched" });

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.method).toBe("PATCH");
  });

  it("DELETE sends correct method", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await api.delete("/api/v1/items/1");

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.method).toBe("DELETE");
  });

  it("appends query params to URL", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await api.get("/api/v1/search", { q: "java", limit: "10" });

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain("q=java");
    expect(url).toContain("limit=10");
  });

  it("filters out null/empty query values", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await api.get("/api/v1/search", { q: "test", empty: "", nil: null });

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain("q=test");
    expect(url).not.toContain("empty");
    expect(url).not.toContain("nil");
  });

  it("returns null for 204 No Content", async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 204 });

    const result = await api.delete("/api/v1/items/1");

    expect(result).toBeNull();
  });

  it("throws on 401 and calls Auth.logout for non-me paths", async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 401 });

    await expect(api.get("/api/v1/search")).rejects.toThrow("Unauthorized");
    expect(authLogoutSpy).toHaveBeenCalled();
  });

  it("does not call Auth.logout on 401 for /api/v1/me", async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 401 });

    await expect(api.get("/api/v1/me")).rejects.toThrow("Unauthorized");
    expect(authLogoutSpy).not.toHaveBeenCalled();
  });

  it("throws with detail message on non-ok response", async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: () => Promise.resolve({ detail: "Bad input" }),
    });

    const err = await api.get("/api/v1/test").catch((e) => e);
    expect(err.message).toBe("Bad input");
    expect(err.status).toBe(400);
  });

  it("uses fallback message when no detail/message in body", async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.resolve({}),
    });

    const err = await api.get("/api/v1/test").catch((e) => e);
    expect(err.message).toContain("500");
  });

  it("handles non-JSON error response", async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new Error("not json")),
    });

    const err = await api.get("/api/v1/test").catch((e) => e);
    expect(err.message).toContain("502");
  });

  it("sends FormData without JSON Content-Type", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    const formData = new FormData();
    formData.append("file", "data");
    await api.post("/api/v1/upload", formData);

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.headers["Content-Type"]).toBeUndefined();
    expect(opts.body).toBe(formData);
  });

  it("sets octet-stream for ArrayBuffer body", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    const buf = new ArrayBuffer(8);
    await api.post("/api/v1/binary", buf);

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.headers["Content-Type"]).toBe("application/octet-stream");
  });

  it("always sends X-Requested-With header", async () => {
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await api.get("/api/v1/test");

    const [, opts] = global.fetch.mock.calls[0];
    expect(opts.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  });
});
