package org.peoplemesh.api;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.IpAllowlistUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Shared authentication/authorization logic for machine-to-machine maintenance endpoints.
 */
public final class MaintenanceAuthHelper {

    private MaintenanceAuthHelper() {}

    public static void assertAuthorized(String key, AppConfig config, HttpHeaders httpHeaders) {
        String expected = config.maintenance().apiKey().orElse(null);
        if (expected == null || expected.isBlank()) {
            throw new ForbiddenException("Maintenance API key not configured");
        }
        if (key == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("Invalid maintenance API key");
        }
        assertIpAllowed(config, httpHeaders);
    }

    private static void assertIpAllowed(AppConfig config, HttpHeaders httpHeaders) {
        String cidrs = config.maintenance().allowedCidrs().orElse(null);
        if (cidrs == null || cidrs.isBlank()) {
            return;
        }
        String callerIp = ClientIpResolver.resolveClientIp(httpHeaders).orElse(null);
        if (callerIp == null || callerIp.isBlank()) {
            throw new ForbiddenException("Cannot determine caller IP for maintenance allowlist check");
        }
        var allowed = Arrays.stream(cidrs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (!IpAllowlistUtils.matchesAnyCidr(callerIp, allowed)) {
            throw new ForbiddenException("Caller IP not in maintenance allowlist");
        }
    }
}
