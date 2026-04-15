package org.peoplemesh.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.config.ConfigProvider;

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
    private static final String BASE_URI = ConfigProvider.getConfig()
            .getOptionalValue("peoplemesh.problems.base-uri", String.class)
            .orElse("about:blank");

    public static ProblemDetail of(int status, String title, String detail) {
        String type = "about:blank".equals(BASE_URI) ? BASE_URI : switch (status) {
            case 400 -> BASE_URI + "/bad-request";
            case 401 -> BASE_URI + "/unauthorized";
            case 403 -> BASE_URI + "/forbidden";
            case 404 -> BASE_URI + "/not-found";
            case 409 -> BASE_URI + "/conflict";
            case 429 -> BASE_URI + "/rate-limit-exceeded";
            default -> BASE_URI + "/internal-error";
        };
        return new ProblemDetail(type, title, status, detail, null);
    }
}
