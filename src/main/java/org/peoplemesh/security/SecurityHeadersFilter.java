package org.peoplemesh.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String PAGE_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com;"
            + " font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; img-src 'self' data: https://randomuser.me https://media.licdn.com https://*.googleusercontent.com https://avatars.githubusercontent.com;"
            + " connect-src 'self';"
            + " frame-ancestors 'none'";

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("X-Frame-Options", "DENY");
        response.getHeaders().putSingle("X-XSS-Protection", "0");
        response.getHeaders().putSingle("Referrer-Policy", "strict-origin-when-cross-origin");
        response.getHeaders().putSingle("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), interest-cohort=()");

        String path = request.getUriInfo().getPath();
        boolean isApi = path.startsWith("/api/") || path.startsWith("/mcp/") || path.startsWith("/q/");
        if (!response.getHeaders().containsKey("Content-Security-Policy")) {
            response.getHeaders().putSingle("Content-Security-Policy", isApi ? API_CSP : PAGE_CSP);
        }
        response.getHeaders().putSingle("Cache-Control", isApi ? "no-store" : "no-cache");
    }
}
