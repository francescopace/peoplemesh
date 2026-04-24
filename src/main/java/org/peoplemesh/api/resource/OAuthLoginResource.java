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
import org.peoplemesh.config.AppConfig;
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

    @Inject
    AppConfig appConfig;

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
        if (outcome.importRedirectUri() != null) {
            return Response.temporaryRedirect(outcome.importRedirectUri()).build();
        }
        if (outcome.isSessionRedirect()) {
            NewCookie sessionCookie = buildSessionCookie(outcome.sessionCookieValue(), isSecure());
            URI after = UriBuilder.fromUri(uriInfo.getBaseUri()).path("/").fragment("/search").build();
            return Response.temporaryRedirect(after).cookie(sessionCookie).build();
        }
        OAuthLoginService.ErrorResponse response = outcome.error();
        return Response.status(response.status())
                .entity(ProblemDetail.of(response.status(), response.title(), response.detail()))
                .build();
    }

    @GET
    @Path("/callback/{provider}/import-finalize")
    public Response importFinalize(
            @PathParam("provider")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
            String provider,
            @jakarta.ws.rs.QueryParam("code") @Size(max = 2048) String code,
            @jakarta.ws.rs.QueryParam("state") @Size(max = 2048) String state
    ) {
        OAuthLoginService.ImportFinalizeOutcome outcome = oAuthLoginService.finalizeImportCallback(
                provider,
                code,
                state,
                callbackUri(provider)
        );
        if (outcome.isSuccess()) {
            return Response.ok(java.util.Map.of(
                    "imported", outcome.imported(),
                    "source", outcome.source()
            )).build();
        }
        OAuthLoginService.ErrorResponse error = outcome.error();
        return Response.status(error.status())
                .entity(ProblemDetail.of(error.status(), error.title(), error.detail()))
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout() {
        NewCookie clearCookie = buildClearCookie(isSecure());
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
        String requestOrigin = uriInfo.getBaseUri().resolve("/").toString().replaceAll("/+$", "");
        String normalizedRequestOrigin = normalizeOrigin(requestOrigin);
        return appConfig.frontend().origin()
                .map(this::normalizeOrigin)
                .filter(origin -> origin != null && !origin.isBlank())
                .orElse(normalizedRequestOrigin);
    }

    private String normalizeOrigin(String rawOrigin) {
        try {
            URI uri = URI.create(rawOrigin).normalize();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            int port = uri.getPort();
            return port > 0
                    ? (scheme.toLowerCase() + "://" + host + ":" + port)
                    : (scheme.toLowerCase() + "://" + host);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isSecure() {
        return uriInfo.getRequestUri().getScheme().equalsIgnoreCase("https");
    }

    private NewCookie buildSessionCookie(String value, boolean secure) {
        return new NewCookie.Builder(SessionService.COOKIE_NAME)
                .value(value)
                .path("/")
                .maxAge(sessionService.sessionMaxAgeSeconds())
                .httpOnly(true)
                .secure(secure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private NewCookie buildClearCookie(boolean secure) {
        return new NewCookie.Builder(SessionService.COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(secure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }
}
