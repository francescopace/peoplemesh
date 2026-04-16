package org.peoplemesh.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.peoplemesh.domain.exception.BusinessException;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException e) {
        return Response.status(e.status())
                .entity(ProblemDetail.of(e.status(), e.title(), e.publicDetail()))
                .build();
    }
}
