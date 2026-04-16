package org.peoplemesh.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.peoplemesh.domain.exception.RateLimitException;

@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitException> {

    @Override
    public Response toResponse(RateLimitException e) {
        return Response.status(429)
                .header("Retry-After", "60")
                .entity(ProblemDetail.of(429, "Too Many Requests", "Search rate limit exceeded"))
                .build();
    }
}
