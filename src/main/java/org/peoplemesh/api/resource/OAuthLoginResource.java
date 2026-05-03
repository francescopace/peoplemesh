package org.peoplemesh.api.resource;

import io.quarkus.security.identity.SecurityIdentity;
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
import org.peoplemesh.domain.dto.AuthProvidersDto;
import org.peoplemesh.service.AuthIdentityService;
import org.peoplemesh.service.OAuthLoginService;
import org.peoplemesh.service.SessionService;

import java.net.URI;
import java.util.List;

@Path("/api/v1/auth")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
public class OAuthLoginResource {

    @Context
    UriInfo uriInfo;

    @Inject
    OAuthLoginService oAuthLoginService;

    @Inject
    SessionService sessionService;

    @Inject
    SecurityIdentity identity;

    @Inject
    AuthIdentityService authIdentityService;

    @Inject
    AppConfig appConfig;

    @GET
    @Path("/login/{provider}")
    public Response login(@PathParam("provider")
                          @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "provider contains invalid characters")
                          String provider,
                          @jakarta.ws.rs.QueryParam("intent") @Size(max = 50) String intent,
                          @jakarta.ws.rs.QueryParam("ctx") @Size(max = 200) String context) {
        OAuthLoginService.LoginOutcome outcome = oAuthLoginService.login(provider, intent, callbackUri(provider), context);
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
            URI after = outcome.sessionRedirectUri() != null
                    ? outcome.sessionRedirectUri()
                    : UriBuilder.fromUri(uriInfo.getBaseUri()).path("/").fragment("/search").build();
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

    @GET
    @Path("/mcp/login")
    @Produces(MediaType.TEXT_HTML)
    public Response mcpLogin() {
        AuthProvidersDto providers = oAuthLoginService.providers();
        List<String> loginProviders = providers.loginProviders();
        if (loginProviders == null || loginProviders.isEmpty()) {
            return Response.status(503)
                    .entity(renderMcpAuthUnavailablePage())
                    .build();
        }
        if (loginProviders.size() == 1) {
            return Response.temporaryRedirect(mcpProviderLoginUri(loginProviders.getFirst())).build();
        }
        return Response.ok(renderMcpProviderChooser(loginProviders)).build();
    }

    @GET
    @Path("/mcp/complete")
    @Produces(MediaType.TEXT_HTML)
    public Response mcpComplete() {
        return Response.ok(renderMcpCompletePage()).build();
    }

    @GET
    @Path("/identity")
    public Response getIdentity() {
        return authIdentityService.resolveCurrentIdentity(identity)
                .map(payload -> Response.ok(payload).build())
                .orElse(Response.noContent().build());
    }

    private String callbackUri(String provider) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("api/v1/auth/callback")
                .path(provider)
                .build()
                .toString();
    }

    private URI mcpProviderLoginUri(String provider) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("api/v1/auth/login")
                .path(provider)
                .queryParam("intent", OAuthLoginService.INTENT_MCP_AUTH)
                .build();
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

    private String renderMcpProviderChooser(List<String> loginProviders) {
        String options = loginProviders.stream()
                .map(provider -> "<li><a href=\"" + mcpProviderLoginUri(provider) + "\">Continue with "
                        + escapeHtml(labelForProvider(provider)) + "</a></li>")
                .reduce("", String::concat);
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>PeopleMesh MCP Authentication</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 2rem; line-height: 1.5; }
                    .card { max-width: 560px; border: 1px solid #ddd; border-radius: 12px; padding: 1.25rem; }
                    ul { padding-left: 1.2rem; }
                    a { color: #0b57d0; text-decoration: none; font-weight: 600; }
                    a:hover { text-decoration: underline; }
                    .muted { color: #555; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Authenticate PeopleMesh for MCP</h1>
                    <p>Select your login provider to continue.</p>
                    <ul>
                """ + options + """
                    </ul>
                    <p class="muted">After sign-in, return to Claude Desktop.</p>
                  </div>
                </body>
                </html>
                """;
    }

    private String renderMcpCompletePage() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>PeopleMesh MCP Authentication Complete</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 2rem; line-height: 1.5; }
                    .card { max-width: 560px; border: 1px solid #ddd; border-radius: 12px; padding: 1.25rem; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Authentication complete</h1>
                    <p>You can close this window and continue in Claude Desktop.</p>
                  </div>
                </body>
                </html>
                """;
    }

    private String renderMcpAuthUnavailablePage() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>PeopleMesh MCP Authentication Unavailable</title>
                </head>
                <body>
                  <h1>Authentication unavailable</h1>
                  <p>No login provider is configured. Configure at least one login provider and retry.</p>
                </body>
                </html>
                """;
    }

    private static String labelForProvider(String provider) {
        return switch (provider) {
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            case "github" -> "GitHub";
            default -> provider;
        };
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
