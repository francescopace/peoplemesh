package org.peoplemesh.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.peoplemesh.api.error.IllegalArgumentExceptionMapper;
import org.peoplemesh.api.error.IllegalStateExceptionMapper;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.api.error.RateLimitExceptionMapper;
import org.peoplemesh.api.error.SecurityExceptionMapper;
import org.peoplemesh.api.error.UnauthorizedExceptionMapper;
import org.peoplemesh.api.error.UnhandledExceptionMapper;
import org.peoplemesh.api.error.ValidationExceptionMapper;
import org.peoplemesh.api.error.WebApplicationExceptionMapper;
import org.peoplemesh.domain.exception.RateLimitException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionMapperTest {

    private final WebApplicationExceptionMapper webMapper = new WebApplicationExceptionMapper();
    private final UnauthorizedExceptionMapper unauthorizedMapper = new UnauthorizedExceptionMapper();
    private final SecurityExceptionMapper securityMapper = new SecurityExceptionMapper();
    private final RateLimitExceptionMapper rateLimitMapper = new RateLimitExceptionMapper();
    private final ValidationExceptionMapper validationMapper = new ValidationExceptionMapper();
    private final IllegalArgumentExceptionMapper illegalArgumentMapper = new IllegalArgumentExceptionMapper();
    private final IllegalStateExceptionMapper illegalStateMapper = new IllegalStateExceptionMapper();
    private final UnhandledExceptionMapper unhandledMapper = new UnhandledExceptionMapper();

    @Test
    void notAllowedException_returns405() {
        Response response = webMapper.toResponse(
                new NotAllowedException(Response.status(405).build()));
        assertEquals(405, response.getStatus());
        assertInstanceOf(ProblemDetail.class, response.getEntity());
    }

    @Test
    void notFoundException_returns404() {
        Response response = webMapper.toResponse(new NotFoundException("not found"));
        assertEquals(404, response.getStatus());
    }

    @Test
    void notAuthorizedException_returns401() {
        Response response = unauthorizedMapper.toResponse(
                new NotAuthorizedException("unauthorized", Response.status(401).build()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void securityException_returns403() {
        Response response = securityMapper.toResponse(new SecurityException("forbidden"));
        assertEquals(403, response.getStatus());
    }

    @Test
    void rateLimitException_returns429_withRetryAfter() {
        Response response = rateLimitMapper.toResponse(new RateLimitException("rate limited"));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeaderString("Retry-After"));
    }

    @Test
    void constraintViolationException_returns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("field.name");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be null");

        Response response = validationMapper.toResponse(
                new ConstraintViolationException("validation", Set.of(violation)));
        assertEquals(400, response.getStatus());
        ProblemDetail pd = (ProblemDetail) response.getEntity();
        assertTrue(pd.detail().contains("name"));
    }

    @Test
    void illegalArgumentException_returns400() {
        Response response = illegalArgumentMapper.toResponse(new IllegalArgumentException("bad input"));
        assertEquals(400, response.getStatus());
    }

    @Test
    void illegalStateException_returns409() {
        Response response = illegalStateMapper.toResponse(new IllegalStateException("conflict"));
        assertEquals(409, response.getStatus());
    }

    @Test
    void unhandledException_returns500() {
        Response response = unhandledMapper.toResponse(new RuntimeException("oops"));
        assertEquals(500, response.getStatus());
        ProblemDetail pd = (ProblemDetail) response.getEntity();
        assertTrue(pd.detail().contains("unexpected"));
    }
}