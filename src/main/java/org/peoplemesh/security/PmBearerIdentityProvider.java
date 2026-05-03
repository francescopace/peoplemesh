package org.peoplemesh.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.service.McpOAuthService;

import java.security.Principal;

@ApplicationScoped
public class PmBearerIdentityProvider implements IdentityProvider<PmBearerAuthenticationRequest> {

    @Inject
    McpOAuthService mcpOAuthService;

    @Override
    public Class<PmBearerAuthenticationRequest> getRequestType() {
        return PmBearerAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(PmBearerAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        McpOAuthService.AccessTokenPrincipal principal = mcpOAuthService.resolveAccessToken(request.accessToken())
                .orElse(null);
        if (principal == null) {
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }
        Principal securityPrincipal = () -> "pm_oauth:" + principal.userId();
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(securityPrincipal)
                .addAttribute("pm.userId", principal.userId())
                .addAttribute("pm.provider", principal.provider())
                .build();
        if (principal.displayName() != null && !principal.displayName().isBlank()) {
            identity = QuarkusSecurityIdentity.builder(identity)
                    .addAttribute("pm.displayName", principal.displayName())
                    .build();
        }
        return Uni.createFrom().item(identity);
    }
}
