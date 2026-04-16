package org.peoplemesh.api.error;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Override
    public Response toResponse(WebApplicationException e) {
        if (e instanceof NotAllowedException) {
            return Response.status(405)
                    .entity(ProblemDetail.of(405, "Method Not Allowed", "Method not allowed"))
                    .build();
        }
        if (e instanceof NotFoundException) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", "Resource not found"))
                    .build();
        }
        int status = e.getResponse() != null ? e.getResponse().getStatus() : 500;
        String title = e.getResponse() != null && e.getResponse().getStatusInfo() != null
                ? e.getResponse().getStatusInfo().getReasonPhrase()
                : "Request failed";
        LOG.debugf("Web application exception status=%d", status);
        return Response.status(status)
                .entity(ProblemDetail.of(status, title, "Request failed"))
                .build();
    }
}
