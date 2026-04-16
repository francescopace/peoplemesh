package org.peoplemesh.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    private static final Logger LOG = Logger.getLogger(SecurityExceptionMapper.class);

    @Override
    public Response toResponse(SecurityException e) {
        LOG.debug("Forbidden request");
        return Response.status(403)
                .entity(ProblemDetail.of(403, "Forbidden", "Forbidden"))
                .build();
    }
}
