package org.peoplemesh.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    private static final Logger LOG = Logger.getLogger(IllegalStateExceptionMapper.class);

    @Override
    public Response toResponse(IllegalStateException e) {
        LOG.debug("Conflict while processing request");
        return Response.status(409)
                .entity(ProblemDetail.of(409, "Conflict", "Conflict"))
                .build();
    }
}
