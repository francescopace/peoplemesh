package org.peoplemesh.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class OAuthHtmlRenderer {

    private OAuthHtmlRenderer() {}

    public static RenderedHtml importSuccess(String jsonData, String source, String origin) {
        String nonce = UUID.randomUUID().toString();
        String encodedJson = encodeBase64(jsonData, 500_000);
        String encodedSource = encodeBase64(source, 200);
        String safeOrigin = escapeForJsString(normalizeOrigin(origin), 2000);
        boolean hasSafeOrigin = safeOrigin != null && !safeOrigin.isBlank();

        String html = """
                <!DOCTYPE html>
                <html><head><title>Importing...</title></head>
                <body>
                <p>Import complete. This window will close automatically.</p>
                <script nonce="%s">
                (function() {
                  var data = JSON.parse(atob("%s"));
                  var msg = {type: "import-result", imported: data, source: atob("%s")};
                  if (window.opener && %s) {
                    window.opener.postMessage(msg, "%s");
                  }
                  window.close();
                })();
                </script>
                </body></html>
                """.formatted(nonce, encodedJson, encodedSource, hasSafeOrigin ? "true" : "false", safeOrigin);

        String csp = "default-src 'none'; script-src 'nonce-" + nonce + "'";
        return new RenderedHtml(html, csp);
    }

    public static RenderedHtml importError(String errorMessage, String origin) {
        String safeOrigin = escapeForJsString(normalizeOrigin(origin), 2000);
        boolean hasSafeOrigin = safeOrigin != null && !safeOrigin.isBlank();
        String nonce = UUID.randomUUID().toString();
        String safeHtmlMsg = SanitizeUtils.sanitize(errorMessage, 500);
        String encodedJsMsg = encodeBase64(errorMessage, 500);

        String html = """
                <!DOCTYPE html>
                <html><head><title>Import Error</title></head>
                <body>
                <p>%s</p>
                <script nonce="%s">
                (function() {
                  if (window.opener && %s) {
                    var errorMsg = atob("%s");
                    window.opener.postMessage({type: "import-error", error: errorMsg}, "%s");
                  }
                  setTimeout(function(){ window.close(); }, 3000);
                })();
                </script>
                </body></html>
                """.formatted(safeHtmlMsg, nonce, hasSafeOrigin ? "true" : "false", encodedJsMsg, safeOrigin);

        String csp = "default-src 'none'; script-src 'nonce-" + nonce + "'";
        return new RenderedHtml(html, csp);
    }

    public record RenderedHtml(String html, String csp) {}

    private static String normalizeOrigin(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank()) return null;
        try {
            URI uri = URI.create(rawOrigin).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) return null;
            int port = uri.getPort();
            return port > 0
                    ? normalizedScheme + "://" + host + ":" + port
                    : normalizedScheme + "://" + host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String escapeForJsString(String value, int maxLength) {
        if (value == null) return null;
        String truncated = value.length() > maxLength ? value.substring(0, maxLength) : value;
        return truncated
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("</", "<\\/")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String encodeBase64(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String truncated = value.length() > maxLength ? value.substring(0, maxLength) : value;
        return Base64.getEncoder().encodeToString(truncated.getBytes(StandardCharsets.UTF_8));
    }
}
