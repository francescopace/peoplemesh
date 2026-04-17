package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.service.OAuthLoginService;
import org.peoplemesh.service.SessionService;

import java.net.URI;

@Path("/api/v1/auth")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class OAuthLoginResource {

    @Context
    UriInfo uriInfo;

    @Inject
    OAuthLoginService oAuthLoginService;

    @Inject
    SessionService sessionService;

    @GET
    @Path("/providers")
    public Response providers() {
        return Response.ok(oAuthLoginService.providers()).build();
    }

    @GET
    @Path("/login/{provider}")
    public Response login(@PathParam("provider")
                          @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
                          String provider,
                          @jakarta.ws.rs.QueryParam("intent") @Size(max = 50) String intent) {
        OAuthLoginService.LoginOutcome outcome = oAuthLoginService.login(provider, intent, callbackUri(provider));
        if (outcome.isRedirect()) {
            return Response.temporaryRedirect(outcome.redirectUri()).build();
        }
        OAuthLoginService.ErrorResponse error = outcome.error();
        return Response.status(error.status())
                .entity(ProblemDetail.of(error.status(), error.title(), error.detail()))
                .build();
    }

    @GET
    @Path("/callback/{provider}")
    public Response callback(
            @PathParam("provider")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
            String provider,
            @jakarta.ws.rs.QueryParam("code") @Size(max = 2048) String code,
            @jakarta.ws.rs.QueryParam("state") @Size(max = 2048) String state,
            @jakarta.ws.rs.QueryParam("error") @Size(max = 200) String error,
            @jakarta.ws.rs.QueryParam("error_description") @Size(max = 1000) String errorDescription) {
        OAuthLoginService.CallbackOutcome outcome = oAuthLoginService.callback(
                provider,
                code,
                state,
                error,
                callbackUri(provider),
                resolveOrigin());
        if (outcome.importHtml() != null) {
            return Response.ok(outcome.importHtml().html(), MediaType.TEXT_HTML)
                    .header("Content-Security-Policy", outcome.importHtml().csp())
                    .build();
        }
        if (outcome.isSessionRedirect()) {
            NewCookie sessionCookie = sessionService.buildSessionCookie(outcome.sessionCookieValue(), isSecure());
            URI after = UriBuilder.fromUri(uriInfo.getBaseUri()).path("/").fragment("/search").build();
            return Response.temporaryRedirect(after).cookie(sessionCookie).build();
        }
        OAuthLoginService.ErrorResponse response = outcome.error();
        return Response.status(response.status())
                .entity(ProblemDetail.of(response.status(), response.title(), response.detail()))
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout() {
        NewCookie clearCookie = sessionService.buildClearCookie(isSecure());
        return Response.noContent().cookie(clearCookie).build();
    }

    private String callbackUri(String provider) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("api/v1/auth/callback")
                .path(provider)
                .build()
                .toString();
    }

    private String resolveOrigin() {
        return uriInfo.getBaseUri().resolve("/").toString().replaceAll("/+$", "");
    }

    private boolean isSecure() {
        return uriInfo.getRequestUri().getScheme().equalsIgnoreCase("https");
    }
}
