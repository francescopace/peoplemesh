package org.peoplemesh.api;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Maps unhandled exceptions to RFC 7807 ProblemDetail responses.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof NotFoundException) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", e.getMessage()))
                    .build();
        }

        if (e instanceof SecurityException) {
            return Response.status(403)
                    .entity(ProblemDetail.of(403, "Forbidden", e.getMessage()))
                    .build();
        }

        if (e instanceof IllegalArgumentException) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", e.getMessage()))
                    .build();
        }

        if (e instanceof IllegalStateException) {
            return Response.status(409)
                    .entity(ProblemDetail.of(409, "Conflict", e.getMessage()))
                    .build();
        }

        LOG.error("Unhandled exception", e);
        return Response.status(500)
                .entity(ProblemDetail.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later."))
                .build();
    }
}
