package org.peoplemesh.api.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.exception.RateLimitException;

import java.util.stream.Collectors;

/**
 * Maps unhandled exceptions to RFC 7807 ProblemDetail responses.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof BusinessException be) {
            return Response.status(be.status())
                    .entity(ProblemDetail.of(be.status(), be.title(), be.publicDetail()))
                    .build();
        }

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

        if (e instanceof NotAuthorizedException) {
            return Response.status(401)
                    .entity(ProblemDetail.of(401, "Unauthorized", "Authentication required"))
                    .build();
        }

        if (e instanceof SecurityException) {
            LOG.debugf(e, "Forbidden request");
            return Response.status(403)
                    .entity(ProblemDetail.of(403, "Forbidden", "Forbidden"))
                    .build();
        }

        if (e instanceof RateLimitException) {
            return Response.status(429)
                    .header("Retry-After", "60")
                    .entity(ProblemDetail.of(429, "Too Many Requests", "Search rate limit exceeded"))
                    .build();
        }

        if (e instanceof ConstraintViolationException cve) {
            String detail = cve.getConstraintViolations().stream()
                    .map(this::formatViolation)
                    .collect(Collectors.joining("; "));
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", detail))
                    .build();
        }

        if (e instanceof IllegalArgumentException) {
            LOG.debugf(e, "Invalid request payload");
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "Invalid request"))
                    .build();
        }

        if (e instanceof IllegalStateException) {
            LOG.debugf(e, "Conflict while processing request");
            return Response.status(409)
                    .entity(ProblemDetail.of(409, "Conflict", "Conflict"))
                    .build();
        }

        if (e instanceof WebApplicationException wae) {
            int status = wae.getResponse() != null ? wae.getResponse().getStatus() : 500;
            String title = wae.getResponse() != null && wae.getResponse().getStatusInfo() != null
                    ? wae.getResponse().getStatusInfo().getReasonPhrase()
                    : "Request failed";
            LOG.debugf(wae, "Web application exception status=%d", status);
            return Response.status(status)
                    .entity(ProblemDetail.of(status, title, "Request failed"))
                    .build();
        }

        LOG.errorf(e, "Unhandled exception");
        return Response.status(500)
                .entity(ProblemDetail.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later."))
                .build();
    }

    private String formatViolation(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return field + ": " + v.getMessage();
    }
}
