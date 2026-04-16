package org.peoplemesh.domain.exception;

public class ForbiddenBusinessException extends BusinessException {
    public ForbiddenBusinessException(String detail) {
        super(403, "Forbidden", detail);
    }
}
