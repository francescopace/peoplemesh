package org.peoplemesh.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.service.SessionService;

import java.security.Principal;

@ApplicationScoped
public class PmSessionIdentityProvider implements IdentityProvider<PmSessionAuthenticationRequest> {

    @Inject
    SessionService sessionService;

    @Override
    public Class<PmSessionAuthenticationRequest> getRequestType() {
        return PmSessionAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(PmSessionAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        SessionService.PmSession session = sessionService.decodeSession(request.cookieValue()).orElse(null);
        if (session == null) {
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }

        Principal principal = () -> "pm_session:" + session.userId();
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addAttribute("pm.userId", session.userId())
                .addAttribute("pm.provider", session.provider())
                .build();
        String displayName = session.displayName();
        if (displayName != null && !displayName.isBlank()) {
            identity = QuarkusSecurityIdentity.builder(identity)
                    .addAttribute("pm.displayName", displayName)
                    .build();
        }
        return Uni.createFrom().item(identity);
    }
}
