package org.peoplemesh.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public final class PmBearerAuthenticationRequest extends BaseAuthenticationRequest {
    private final String accessToken;

    public PmBearerAuthenticationRequest(String accessToken) {
        this.accessToken = accessToken;
    }

    public String accessToken() {
        return accessToken;
    }
}
