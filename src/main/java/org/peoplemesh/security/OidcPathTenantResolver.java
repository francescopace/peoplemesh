package org.peoplemesh.security;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Always returns {@code null} so the default OIDC tenant (bearer-token
 * verification for MCP) is used.  Social-provider OAuth2 is handled
 * entirely by {@code OAuthLoginResource} + {@code OAuthTokenExchangeService}
 * without Quarkus OIDC web-app flow.
 */
@ApplicationScoped
public class OidcPathTenantResolver implements TenantResolver {

    @Override
    public String resolve(RoutingContext context) {
        return null;
    }
}
