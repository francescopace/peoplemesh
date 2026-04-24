package org.peoplemesh.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/**
 * Authentication request carrying the signed PeopleMesh session cookie.
 */
public final class PmSessionAuthenticationRequest extends BaseAuthenticationRequest {
    private final String cookieValue;

    public PmSessionAuthenticationRequest(String cookieValue) {
        this.cookieValue = cookieValue;
    }

    public String cookieValue() {
        return cookieValue;
    }
}
