package org.peoplemesh.security;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Redis-backed rate limiter using atomic INCR.
 * Enforces per-IP and per-user limits on API/MCP endpoints.
 * Fails open (allows requests) if Redis is unavailable.
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);
    private static final String IP_PREFIX = "ratelimit:ip:";
    private static final String USER_PREFIX = "ratelimit:user:";

    @ConfigProperty(name = "peoplemesh.rate-limit.ip.max-requests", defaultValue = "100")
    int ipMaxRequests;

    @ConfigProperty(name = "peoplemesh.rate-limit.ip.window-seconds", defaultValue = "60")
    int ipWindowSeconds;

    @ConfigProperty(name = "peoplemesh.rate-limit.user.max-requests", defaultValue = "30")
    int userMaxRequests;

    @ConfigProperty(name = "peoplemesh.rate-limit.user.window-seconds", defaultValue = "60")
    int userWindowSeconds;

    @Inject
    RedisDataSource redisDataSource;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("/api/") && !path.startsWith("/mcp/")) {
            return;
        }

        ValueCommands<String, Long> commands;
        try {
            commands = redisDataSource.value(String.class, Long.class);
        } catch (Exception e) {
            LOG.debug("Redis unavailable for rate limiting, allowing request", e);
            return;
        }

        String clientIp = extractIp(ctx);
        if (clientIp != null) {
            if (!checkAndIncrement(commands, IP_PREFIX + clientIp, ipMaxRequests, ipWindowSeconds)) {
                ctx.abortWith(rateLimitResponse(ipWindowSeconds, "IP rate limit exceeded"));
                return;
            }
        }

        String userId = extractUserId(ctx);
        if (userId != null) {
            if (!checkAndIncrement(commands, USER_PREFIX + userId, userMaxRequests, userWindowSeconds)) {
                ctx.abortWith(rateLimitResponse(userWindowSeconds, "User rate limit exceeded"));
            }
        }
    }

    private boolean checkAndIncrement(ValueCommands<String, Long> commands,
                                      String key, int maxRequests, int windowSeconds) {
        try {
            Long newVal = commands.incr(key);
            if (newVal == 1L) {
                commands.getex(key, new io.quarkus.redis.datasource.value.GetExArgs()
                        .ex(Duration.ofSeconds(windowSeconds)));
            }
            return newVal <= maxRequests;
        } catch (Exception e) {
            LOG.debug("Redis error during rate limit check, allowing request", e);
            return true;
        }
    }

    private Response rateLimitResponse(int retryAfter, String detail) {
        return Response.status(429)
                .header("Retry-After", retryAfter)
                .entity(org.peoplemesh.api.ProblemDetail.of(429, "Too Many Requests", detail))
                .build();
    }

    private String extractIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return ctx.getHeaderString("X-Real-IP");
    }

    private String extractUserId(ContainerRequestContext ctx) {
        var secCtx = ctx.getSecurityContext();
        if (secCtx != null && secCtx.getUserPrincipal() != null) {
            return secCtx.getUserPrincipal().getName();
        }
        return null;
    }
}
