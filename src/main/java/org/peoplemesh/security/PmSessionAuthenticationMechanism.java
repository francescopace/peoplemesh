package org.peoplemesh.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.service.SessionService;

import java.util.Optional;
import java.util.Set;

/**
 * Bridges the signed PeopleMesh session cookie into Quarkus SecurityIdentity.
 */
@ApplicationScoped
public class PmSessionAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
                                              IdentityProviderManager identityProviderManager) {
        String path = context.normalizedPath();
        if (path == null || (!path.startsWith("/api/") && !path.startsWith("/mcp/"))) {
            return Uni.createFrom().nullItem();
        }

        io.vertx.core.http.Cookie cookie = context.request().getCookie(SessionService.COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Uni.createFrom().nullItem();
        }

        return identityProviderManager.authenticate(new PmSessionAuthenticationRequest(cookie.getValue()));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().optional(Optional.empty());
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(PmSessionAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE,
                SessionService.COOKIE_NAME));
    }
}
