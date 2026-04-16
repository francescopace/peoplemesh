package org.peoplemesh.domain.exception;

public class NotFoundBusinessException extends BusinessException {
    public NotFoundBusinessException(String detail) {
        super(404, "Not Found", detail);
    }
}
