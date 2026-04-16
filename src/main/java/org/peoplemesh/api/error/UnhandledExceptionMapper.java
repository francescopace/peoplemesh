package org.peoplemesh.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        LOG.error("Unhandled exception", e);
        return Response.status(500)
                .entity(ProblemDetail.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later."))
                .build();
    }
}
