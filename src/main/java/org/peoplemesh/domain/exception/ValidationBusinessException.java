package org.peoplemesh.domain.exception;

public class ValidationBusinessException extends BusinessException {
    public ValidationBusinessException(String detail) {
        super(400, "Bad Request", detail);
    }
}
