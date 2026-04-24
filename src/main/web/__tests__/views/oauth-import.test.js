import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";

let renderOAuthImport;
let finalizeImportFromOAuthCallbackMock;
let closeSpy;
let openerPostMessage;

describe("oauth import popup view", () => {
  beforeEach(async () => {
    vi.useFakeTimers();
    vi.resetModules();
    document.body.innerHTML = "";

    finalizeImportFromOAuthCallbackMock = vi.fn();
    openerPostMessage = vi.fn();
    closeSpy = vi.spyOn(window, "close").mockImplementation(() => {});

    Object.defineProperty(window, "opener", {
      configurable: true,
      writable: true,
      value: { postMessage: openerPostMessage },
    });

    vi.doMock("../../assets/js/services/auth-service.js", () => ({
      finalizeImportFromOAuthCallback: finalizeImportFromOAuthCallbackMock,
    }));
    const mod = await import("../../assets/js/views/oauth-import.js");
    renderOAuthImport = mod.renderOAuthImport;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("finalizes import and posts import-result to opener", async () => {
    window.location.hash = "#/oauth/import?provider=github&code=abc&state=xyz";
    finalizeImportFromOAuthCallbackMock.mockResolvedValue({
      imported: { professional: { roles: ["Engineer"] } },
      source: "github",
    });

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderOAuthImport(container);

    expect(finalizeImportFromOAuthCallbackMock).toHaveBeenCalledWith(
      "github",
      { code: "abc", state: "xyz" }
    );
    expect(openerPostMessage).toHaveBeenCalledWith(
      {
        type: "import-result",
        imported: { professional: { roles: ["Engineer"] } },
        source: "github",
      },
      window.location.origin
    );
    expect(closeSpy).toHaveBeenCalled();
  });

  it("posts import-error and closes when callback carries oauth error", async () => {
    window.location.hash = "#/oauth/import?provider=github&error=OAuth%20callback%20failed";

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderOAuthImport(container);

    expect(finalizeImportFromOAuthCallbackMock).not.toHaveBeenCalled();
    expect(openerPostMessage).toHaveBeenCalledWith(
      {
        type: "import-error",
        error: "OAuth callback failed",
      },
      window.location.origin
    );
    vi.runAllTimers();
    expect(closeSpy).toHaveBeenCalled();
  });

  it("posts import-error when finalize fails", async () => {
    window.location.hash = "#/oauth/import?provider=github&code=abc&state=xyz";
    finalizeImportFromOAuthCallbackMock.mockRejectedValue(Object.assign(new Error("Request failed (502)"), { status: 502 }));

    const container = document.createElement("div");
    document.body.appendChild(container);
    await renderOAuthImport(container);

    expect(openerPostMessage).toHaveBeenCalledWith(
      {
        type: "import-error",
        error: "Import failed",
      },
      window.location.origin
    );
    vi.runAllTimers();
    expect(closeSpy).toHaveBeenCalled();
  });
});
