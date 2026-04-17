package org.peoplemesh.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.service.CvProfileMergeService;
import org.peoplemesh.service.OAuthCallbackService;
import org.peoplemesh.service.OAuthTokenExchangeService;
import org.peoplemesh.service.SessionService;
import org.peoplemesh.util.OAuthHtmlRenderer;

import java.net.URI;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OAuthLoginApplicationService {

    private static final Logger LOG = Logger.getLogger(OAuthLoginApplicationService.class);
    private static final String INTENT_PROFILE_IMPORT = "profile_import";
    private static final List<String> PROVIDER_ORDER = List.of("google", "microsoft", "github");

    @Inject
    SessionService sessionService;

    @Inject
    OAuthTokenExchangeService tokenExchangeService;

    @Inject
    OAuthCallbackService oAuthCallbackService;

    @Inject
    ObjectMapper objectMapper;

    public Map<String, Object> providers() {
        List<String> loginProviders = PROVIDER_ORDER.stream()
                .filter(tokenExchangeService::isLoginEnabled)
                .toList();
        List<String> configuredProviders = PROVIDER_ORDER.stream()
                .filter(tokenExchangeService::isProviderEnabled)
                .toList();
        return Map.of("providers", loginProviders, "configured", configuredProviders);
    }

    public Response login(String provider, String intent, UriInfo uriInfo) {
        if (!tokenExchangeService.isProviderEnabled(provider)) {
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                    .entity(ProblemDetail.of(501, "Not Implemented",
                            "OAuth login is not configured for provider: " + provider))
                    .build();
        }
        String normalizedIntent = normalizeIntent(provider, intent);
        String redirectUri = callbackUri(provider, uriInfo);
        String state = sessionService.signOAuthState(provider, normalizedIntent);
        URI authorize = tokenExchangeService.buildAuthorizeUri(provider, redirectUri, state);
        if (authorize == null) {
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                    .entity(ProblemDetail.of(501, "Not Implemented",
                            "OAuth login is not implemented for provider: " + provider))
                    .build();
        }
        return Response.temporaryRedirect(authorize).build();
    }

    public Response callback(
            String provider,
            String code,
            String state,
            String error,
            UriInfo uriInfo
    ) {
        LOG.debugf("OAuth callback: provider=%s code=%s state=%s error=%s",
                provider, code != null ? "present" : "null",
                state != null ? "present" : "null", error);

        boolean isImport = state != null && !state.isBlank();
        if (isImport) {
            SessionService.OAuthStatePayload statePayload = sessionService.verifyOAuthState(state, provider);
            isImport = statePayload != null && INTENT_PROFILE_IMPORT.equals(statePayload.intent());
        }

        if (error != null && !error.isBlank()) {
            LOG.warnf("OAuth callback error from %s: %s", provider, error);
            if (isImport) {
                return renderErrorHtml(uriInfo, "OAuth callback failed");
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "OAuth callback failed"))
                    .build();
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            LOG.warn("OAuth callback rejected: missing code or state");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Missing code or state"))
                    .build();
        }
        SessionService.OAuthStatePayload statePayload = sessionService.verifyOAuthState(state, provider);
        if (statePayload == null) {
            LOG.warnf("OAuth callback rejected: invalid/expired state for provider=%s", provider);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Bad Request", "Invalid or expired state"))
                    .build();
        }

        String redirectUri = callbackUri(provider, uriInfo);
        if (INTENT_PROFILE_IMPORT.equals(statePayload.intent())) {
            return handleImportCallback(provider, code, redirectUri, uriInfo);
        }

        OAuthTokenExchangeService.OidcSubject subject;
        try {
            subject = tokenExchangeService.exchangeAndResolveSubject(provider, code, redirectUri);
        } catch (Exception e) {
            LOG.errorf(e, "Token exchange failed for provider=%s", provider);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(ProblemDetail.of(502, "Bad Gateway", "Token exchange failed"))
                    .build();
        }
        if (subject == null) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(ProblemDetail.of(502, "Bad Gateway", "Token exchange failed"))
                    .build();
        }

        OAuthCallbackService.LoginResult result = oAuthCallbackService.handleLogin(provider, subject);
        String cookieValue = sessionService.encodeSession(result.userId(), provider, result.displayName());
        NewCookie sessionCookie = sessionService.buildSessionCookie(cookieValue, isSecure(uriInfo));
        URI after = UriBuilder.fromUri(uriInfo.getBaseUri()).path("/").fragment("/search").build();
        return Response.temporaryRedirect(after).cookie(sessionCookie).build();
    }

    public Response logout(UriInfo uriInfo) {
        NewCookie clearCookie = sessionService.buildClearCookie(isSecure(uriInfo));
        return Response.noContent().cookie(clearCookie).build();
    }

    private Response handleImportCallback(String provider, String code, String redirectUri, UriInfo uriInfo) {
        try {
            ProfileSchema importSchema = oAuthCallbackService.handleImport(provider, code, redirectUri);
            String source = CvProfileMergeService.sourceForProvider(provider);
            String origin = resolveOrigin(uriInfo);
            String jsonData = objectMapper.writeValueAsString(importSchema);
            OAuthHtmlRenderer.RenderedHtml rendered = OAuthHtmlRenderer.importSuccess(jsonData, source, origin);
            return Response.ok(rendered.html(), MediaType.TEXT_HTML)
                    .header("Content-Security-Policy", rendered.csp())
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "Import callback failed for provider=%s", provider);
            return renderErrorHtml(uriInfo, "Import failed");
        }
    }

    private Response renderErrorHtml(UriInfo uriInfo, String errorMessage) {
        OAuthHtmlRenderer.RenderedHtml rendered = OAuthHtmlRenderer.importError(errorMessage, resolveOrigin(uriInfo));
        return Response.ok(rendered.html(), MediaType.TEXT_HTML)
                .header("Content-Security-Policy", rendered.csp())
                .build();
    }

    private String callbackUri(String provider, UriInfo uriInfo) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("api/v1/auth/callback")
                .path(provider)
                .build()
                .toString();
    }

    private String normalizeIntent(String provider, String intent) {
        if (INTENT_PROFILE_IMPORT.equals(intent) && tokenExchangeService.isProviderEnabled(provider)) {
            return INTENT_PROFILE_IMPORT;
        }
        return "";
    }

    private String resolveOrigin(UriInfo uriInfo) {
        return uriInfo.getBaseUri().resolve("/").toString().replaceAll("/+$", "");
    }

    private boolean isSecure(UriInfo uriInfo) {
        return uriInfo.getRequestUri().getScheme().equalsIgnoreCase("https");
    }
}
