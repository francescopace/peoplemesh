package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.peoplemesh.application.OAuthLoginApplicationService;

@Path("/api/v1/auth")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class OAuthLoginResource {

    @Context
    UriInfo uriInfo;

    @Inject
    OAuthLoginApplicationService oAuthLoginApplicationService;

    @GET
    @Path("/providers")
    public Response providers() {
        return Response.ok(oAuthLoginApplicationService.providers()).build();
    }

    @GET
    @Path("/login/{provider}")
    public Response login(@PathParam("provider")
                          @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
                          String provider,
                          @jakarta.ws.rs.QueryParam("intent") String intent) {
        return oAuthLoginApplicationService.login(provider, intent, uriInfo);
    }

    @GET
    @Path("/callback/{provider}")
    public Response callback(
            @PathParam("provider")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
            String provider,
            @jakarta.ws.rs.QueryParam("code") String code,
            @jakarta.ws.rs.QueryParam("state") String state,
            @jakarta.ws.rs.QueryParam("error") String error,
            @jakarta.ws.rs.QueryParam("error_description") String errorDescription) {
        return oAuthLoginApplicationService.callback(provider, code, state, error, uriInfo);
    }

    @POST
    @Path("/logout")
    public Response logout() {
        return oAuthLoginApplicationService.logout(uriInfo);
    }
}
