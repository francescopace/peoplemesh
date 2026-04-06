package org.peoplemesh.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("X-Frame-Options", "DENY");
        response.getHeaders().putSingle("X-XSS-Protection", "0");
        response.getHeaders().putSingle("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'");
        response.getHeaders().putSingle("Referrer-Policy", "strict-origin-when-cross-origin");
        response.getHeaders().putSingle("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), interest-cohort=()");
        response.getHeaders().putSingle("Cache-Control", "no-store");
    }
}
