package org.peoplemesh.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import org.peoplemesh.service.SessionService;

import java.util.Optional;
import java.util.Set;

/**
 * Bridges the signed PeopleMesh session cookie into Quarkus SecurityIdentity.
 */
@ApplicationScoped
public class PmSessionAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String MCP_LOGIN_PATH = "/api/v1/auth/mcp/login";
    private static final String MCP_BEARER_CHALLENGE = "Bearer realm=\"peoplemesh-mcp\"";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
                                              IdentityProviderManager identityProviderManager) {
        String path = context.normalizedPath();
        if (!isSecuredPath(path)) {
            return Uni.createFrom().nullItem();
        }

        String bearerToken = extractBearerToken(context.request().getHeader(HttpHeaders.AUTHORIZATION));
        if (bearerToken != null && !bearerToken.isBlank()) {
            return identityProviderManager.authenticate(new PmBearerAuthenticationRequest(bearerToken));
        }

        io.vertx.core.http.Cookie cookie = context.request().getCookie(SessionService.COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Uni.createFrom().nullItem();
        }

        return identityProviderManager.authenticate(new PmSessionAuthenticationRequest(cookie.getValue()));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        String path = context.normalizedPath();
        if (path != null && path.startsWith("/mcp")) {
            HttpMethod method = context.request().method();
            if (HttpMethod.GET.equals(method)) {
                return Uni.createFrom().item(new ChallengeData(302, HttpHeaders.LOCATION, MCP_LOGIN_PATH));
            }
            String resourceMetadataUri = buildResourceMetadataUri(context);
            String challenge = MCP_BEARER_CHALLENGE + ", resource_metadata=\"" + resourceMetadataUri + "\"";
            return Uni.createFrom().item(new ChallengeData(401, HttpHeaders.WWW_AUTHENTICATE, challenge));
        }
        return Uni.createFrom().optional(Optional.empty());
    }

    private static boolean isSecuredPath(String path) {
        return path != null && (path.startsWith("/api/") || path.startsWith("/mcp"));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(PmSessionAuthenticationRequest.class, PmBearerAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE,
                SessionService.COOKIE_NAME));
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String buildResourceMetadataUri(RoutingContext context) {
        String absoluteUri = context.request().absoluteURI();
        if (absoluteUri == null || absoluteUri.isBlank()) {
            return "/.well-known/oauth-protected-resource/mcp";
        }
        try {
            java.net.URI requestUri = java.net.URI.create(absoluteUri);
            String origin = requestUri.getScheme() + "://" + requestUri.getAuthority();
            return origin + "/.well-known/oauth-protected-resource/mcp";
        } catch (IllegalArgumentException e) {
            return "/.well-known/oauth-protected-resource/mcp";
        }
    }
}
