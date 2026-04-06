package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.service.AuditService;
import org.peoplemesh.service.EncryptionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AuthResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    AuditService auditService;

    @Inject
    EncryptionService encryptionService;

    @POST
    @Path("/register")
    @Transactional
    public Response registerOrLogin() {
        String provider = resolveProvider();
        String subject = identity.getPrincipal().getName();

        UserIdentity user = UserIdentity.findByOauth(provider, subject)
                .orElseGet(() -> {
                    UserIdentity newUser = new UserIdentity();
                    newUser.oauthProvider = provider;
                    newUser.oauthSubject = subject;
                    newUser.persist();
                    encryptionService.createKeyIfAbsent(newUser.id);
                    return newUser;
                });

        auditService.log(user.id, "AUTH_LOGIN", null);

        return Response.ok(Map.of(
                "user_id", user.id,
                "provider", user.oauthProvider,
                "created", user.createdAt
        )).build();
    }

    @GET
    @Path("/me")
    public Response whoAmI() {
        String provider = resolveProvider();
        String subject = identity.getPrincipal().getName();

        return UserIdentity.findByOauth(provider, subject)
                .map(user -> Response.ok(Map.of(
                        "user_id", user.id,
                        "provider", user.oauthProvider
                )).build())
                .orElse(Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "User not registered"))
                        .build());
    }

    private String resolveProvider() {
        if (identity.getAttribute("quarkus.identity.provider") != null) {
            return identity.getAttribute("quarkus.identity.provider").toString();
        }
        return identity.getAttribute("tenant-id") != null
                ? identity.getAttribute("tenant-id").toString()
                : "unknown";
    }
}
