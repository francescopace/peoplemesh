package org.peoplemesh.security;

import java.util.Set;
import java.util.regex.Pattern;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * Early Vert.x filter that rejects scanner/bot probe requests before they
 * reach the OIDC, security, or JAX-RS layers — avoiding noisy stack traces
 * and wasting resources on illegitimate traffic.
 *
 * Runs at a high priority so it executes before OIDC/auth handlers
 * consume the request body (which would cause "Request has already been
 * read" errors on HTTP/2 when ctx.next() forwards to RESTEasy Reactive).
 */
@ApplicationScoped
public class BotBlockFilter {

    private static final Logger LOG = Logger.getLogger(BotBlockFilter.class);

    private static final int PRIORITY = 10_000;

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".php", ".asp", ".aspx", ".cgi", ".jsp",
            ".env", ".git", ".bak", ".sql", ".zip",
            ".tar", ".gz", ".rar", ".7z",
            ".xml", ".yml", ".yaml", ".ini", ".conf", ".cfg",
            ".log", ".old", ".orig", ".swp", ".DS_Store"
    );

    private static final Pattern BLOCKED_PATH_PATTERN = Pattern.compile(
            "(?i)^/("
                    + "wp-admin|wp-content|wp-includes|wp-login|wordpress"
                    + "|phpmyadmin|pma|myadmin|mysqladmin"
                    + "|admin|administrator|manager|console"
                    + "|cgi-bin|scripts|shell|cmd"
                    + "|vendor|node_modules|bower_components"
                    + "|\\.well-known/security\\.txt"
                    + "|\\.git|\\.svn|\\.hg|\\.env"
                    + "|xmlrpc|eval-stdin|telescope"
                    + "|actuator|debug|config|setup"
                    + "|backup|bkp|dump|db"
                    + "|login|signin|register"
                    + ")(/.*|$)"
    );

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/api/",
            "/q/",
            "/mcp/"
    );

    public void register(@Observes Filters filters) {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext ctx) {
        String path = ctx.normalizedPath();

        if (isAllowed(path)) {
            ctx.next();
            return;
        }

        if (isBlocked(path)) {
            LOG.debugf("Blocked bot probe: %s %s", ctx.request().method(), path);
            reject(ctx);
            return;
        }

        ctx.next();
    }

    private boolean isAllowed(String path) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlocked(String path) {
        String lower = path.toLowerCase();

        for (String ext : BLOCKED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }

        return BLOCKED_PATH_PATTERN.matcher(path).find();
    }

    private void reject(RoutingContext ctx) {
        HttpServerResponse response = ctx.response();
        response.setStatusCode(404);
        response.putHeader("Content-Type", "text/plain");
        response.end("Not Found");
    }
}
