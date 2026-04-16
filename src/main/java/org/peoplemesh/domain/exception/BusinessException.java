package org.peoplemesh.domain.exception;

public class BusinessException extends RuntimeException {
    private final int status;
    private final String title;
    private final String publicDetail;

    public BusinessException(int status, String title, String publicDetail) {
        super(publicDetail);
        this.status = status;
        this.title = title;
        this.publicDetail = publicDetail;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String publicDetail() {
        return publicDetail;
    }
}
