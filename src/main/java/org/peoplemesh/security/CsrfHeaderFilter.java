package org.peoplemesh.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.peoplemesh.api.error.ProblemDetail;

import java.util.Set;

/**
 * Rejects state-changing API requests that lack the {@code X-Requested-With} header.
 * This prevents cross-site request forgery for cookie-authenticated endpoints
 * because browsers will not send custom headers on cross-origin requests
 * unless explicitly allowed by CORS pre-flight.
 */
@Provider
public class CsrfHeaderFilter implements ContainerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!path.startsWith("/api/")) {
            return;
        }

        if (SAFE_METHODS.contains(request.getMethod())) {
            return;
        }

        // OAuth callbacks arrive as browser redirects — no custom header possible
        if (path.startsWith("/api/v1/auth/callback/") || path.startsWith("/api/v1/auth/login/")) {
            return;
        }

        // Maintenance endpoints use X-Maintenance-Key (machine-to-machine, not browser)
        if (path.startsWith("/api/v1/maintenance/")) {
            return;
        }

        String xrw = request.getHeaderString("X-Requested-With");
        if (xrw == null || xrw.isBlank()) {
            request.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(ProblemDetail.of(
                            Response.Status.FORBIDDEN.getStatusCode(),
                            "Forbidden",
                            "Missing X-Requested-With header",
                            request.getUriInfo().getPath()
                    ))
                    .build());
        }
    }
}
