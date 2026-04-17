package org.peoplemesh.util;

import jakarta.ws.rs.core.HttpHeaders;

import java.util.Optional;

/**
 * Resolves caller IP from trusted forwarding headers.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static Optional<String> resolveClientIp(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        String forwardedFor = headers.getHeaderString("X-Forwarded-For");
        String fromForwardedFor = firstIp(forwardedFor);
        if (fromForwardedFor != null) {
            return Optional.of(fromForwardedFor);
        }
        String realIp = firstIp(headers.getHeaderString("X-Real-IP"));
        if (realIp != null) {
            return Optional.of(realIp);
        }
        return Optional.empty();
    }

    private static String firstIp(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return null;
        }
        String first = rawHeader.split(",")[0].trim();
        return first.isBlank() ? null : first;
    }
}
