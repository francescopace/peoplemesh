package org.peoplemesh.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7807 Problem Details response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance
) {
    public static ProblemDetail of(int status, String title, String detail) {
        String type = switch (status) {
            case 400 -> "https://peoplemesh.io/problems/bad-request";
            case 401 -> "https://peoplemesh.io/problems/unauthorized";
            case 403 -> "https://peoplemesh.io/problems/forbidden";
            case 404 -> "https://peoplemesh.io/problems/not-found";
            case 409 -> "https://peoplemesh.io/problems/conflict";
            case 429 -> "https://peoplemesh.io/problems/rate-limit-exceeded";
            default -> "https://peoplemesh.io/problems/internal-error";
        };
        return new ProblemDetail(type, title, status, detail, null);
    }
}
