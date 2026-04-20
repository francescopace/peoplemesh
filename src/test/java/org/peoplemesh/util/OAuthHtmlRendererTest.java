package org.peoplemesh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthHtmlRendererTest {

    @Test
    void importSuccess_containsJsonDataAndSource() {
        String json = "{\"name\":\"Alice\"}";
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importSuccess(json, "github", "https://example.com");

        assertFalse(result.html().contains("{\"name\":\"Alice\"}"));
        assertFalse(result.html().contains("github"));
        assertTrue(result.html().contains("JSON.parse(atob("));
        assertTrue(result.html().contains("import-result"));
        assertTrue(result.html().contains("window.opener.postMessage"));
    }

    @Test
    void importSuccess_cspContainsNonce() {
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importSuccess("{}", "google", "https://example.com");

        assertTrue(result.csp().startsWith("default-src 'none'; script-src 'nonce-"));
        assertTrue(result.html().contains("nonce=\""));
    }

    @Test
    void importSuccess_escapesClosingScriptTag() {
        String json = "{\"x\":\"</script>\"}";
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importSuccess(json, "src", "https://example.com");

        assertFalse(result.html().contains("{\"x\":\"</script>\"}"));
        assertTrue(result.html().contains("JSON.parse(atob("));
    }

    @Test
    void importSuccess_sanitizesOrigin() {
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importSuccess(
                "{}", "src", "https://example.com/<script>alert(1)</script>");

        assertFalse(result.html().contains("<script>alert"));
    }

    @Test
    void importError_containsErrorMessage() {
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importError("Something went wrong", "https://example.com");

        assertTrue(result.html().contains("Something went wrong"));
        assertTrue(result.html().contains("import-error"));
        assertTrue(result.html().contains("window.close()"));
    }

    @Test
    void importError_cspContainsNonce() {
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importError("err", "https://example.com");

        assertTrue(result.csp().startsWith("default-src 'none'; script-src 'nonce-"));
    }

    @Test
    void importError_escapesSpecialCharacters() {
        OAuthHtmlRenderer.RenderedHtml result = OAuthHtmlRenderer.importError(
                "line1\nline2\r\"quoted\"</script>", "https://example.com");

        assertTrue(result.html().contains("line1"));
        assertTrue(result.html().contains("&quot;quoted&quot;"));
        assertFalse(result.html().contains("\"quoted\"</script>"));
    }

    @Test
    void renderedHtml_recordAccessors() {
        OAuthHtmlRenderer.RenderedHtml r = new OAuthHtmlRenderer.RenderedHtml("html", "csp");
        assertEquals("html", r.html());
        assertEquals("csp", r.csp());
    }
}
