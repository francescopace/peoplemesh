package org.peoplemesh.util;

import java.util.UUID;

public final class OAuthHtmlRenderer {

    private OAuthHtmlRenderer() {}

    public static RenderedHtml importSuccess(String jsonData, String source, String origin) {
        String nonce = UUID.randomUUID().toString();
        String safeJson = jsonData.replace("</", "<\\/");
        String safeSource = SanitizeUtils.sanitize(source, 200);
        String safeOrigin = SanitizeUtils.sanitize(origin, 2000);

        String html = """
                <!DOCTYPE html>
                <html><head><title>Importing...</title></head>
                <body>
                <p>Import complete. This window will close automatically.</p>
                <script nonce="%s">
                (function() {
                  var data = %s;
                  var msg = {type: "import-result", imported: data, source: "%s"};
                  if (window.opener) {
                    window.opener.postMessage(msg, "%s");
                  }
                  window.close();
                })();
                </script>
                </body></html>
                """.formatted(nonce, safeJson, safeSource, safeOrigin);

        String csp = "default-src 'none'; script-src 'nonce-" + nonce + "'";
        return new RenderedHtml(html, csp);
    }

    public static RenderedHtml importError(String errorMessage, String origin) {
        String safeOrigin = SanitizeUtils.sanitize(origin, 2000);
        String nonce = UUID.randomUUID().toString();
        String safeHtmlMsg = SanitizeUtils.sanitize(errorMessage, 500);
        String safeJsMsg = errorMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("</", "<\\/")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        String html = """
                <!DOCTYPE html>
                <html><head><title>Import Error</title></head>
                <body>
                <p>%s</p>
                <script nonce="%s">
                (function() {
                  if (window.opener) {
                    window.opener.postMessage({type: "import-error", error: "%s"}, "%s");
                  }
                  setTimeout(function(){ window.close(); }, 3000);
                })();
                </script>
                </body></html>
                """.formatted(safeHtmlMsg, nonce, safeJsMsg, safeOrigin);

        String csp = "default-src 'none'; script-src 'nonce-" + nonce + "'";
        return new RenderedHtml(html, csp);
    }

    public record RenderedHtml(String html, String csp) {}
}
