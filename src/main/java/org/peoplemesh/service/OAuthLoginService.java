package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.util.OAuthHtmlRenderer;

import java.net.URI;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OAuthLoginService {

    private static final Logger LOG = Logger.getLogger(OAuthLoginService.class);
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

    public LoginOutcome login(String provider, String intent, String callbackUri) {
        if (!tokenExchangeService.isProviderEnabled(provider)) {
            return LoginOutcome.error(501, "Not Implemented",
                    "OAuth login is not configured for provider: " + provider);
        }
        String normalizedIntent = normalizeIntent(provider, intent);
        String state = sessionService.signOAuthState(provider, normalizedIntent);
        URI authorize = tokenExchangeService.buildAuthorizeUri(provider, callbackUri, state);
        if (authorize == null) {
            return LoginOutcome.error(501, "Not Implemented",
                    "OAuth login is not implemented for provider: " + provider);
        }
        return LoginOutcome.redirect(authorize);
    }

    public CallbackOutcome callback(
            String provider,
            String code,
            String state,
            String error,
            String callbackUri,
            String origin
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
                return CallbackOutcome.importHtml(OAuthHtmlRenderer.importError("OAuth callback failed", origin));
            }
            return CallbackOutcome.error(400, "Bad Request", "OAuth callback failed");
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            LOG.warn("OAuth callback rejected: missing code or state");
            return CallbackOutcome.error(400, "Bad Request", "Missing code or state");
        }

        SessionService.OAuthStatePayload statePayload = sessionService.verifyOAuthState(state, provider);
        if (statePayload == null) {
            LOG.warnf("OAuth callback rejected: invalid/expired state for provider=%s", provider);
            return CallbackOutcome.error(400, "Bad Request", "Invalid or expired state");
        }

        if (INTENT_PROFILE_IMPORT.equals(statePayload.intent())) {
            return handleImportCallback(provider, code, callbackUri, origin);
        }

        OidcSubject subject;
        try {
            subject = tokenExchangeService.exchangeAndResolveSubject(provider, code, callbackUri);
        } catch (Exception e) {
            LOG.errorf(e, "Token exchange failed for provider=%s", provider);
            return CallbackOutcome.error(502, "Bad Gateway", "Token exchange failed");
        }
        if (subject == null) {
            return CallbackOutcome.error(502, "Bad Gateway", "Token exchange failed");
        }

        OAuthCallbackService.LoginResult result = oAuthCallbackService.handleLogin(provider, subject);
        String cookieValue = sessionService.encodeSession(result.userId(), provider, result.displayName());
        return CallbackOutcome.sessionRedirect(cookieValue);
    }

    private CallbackOutcome handleImportCallback(String provider, String code, String callbackUri, String origin) {
        try {
            ProfileSchema importSchema = oAuthCallbackService.handleImport(provider, code, callbackUri);
            String source = CvProfileMergeService.sourceForProvider(provider);
            String jsonData = objectMapper.writeValueAsString(importSchema);
            return CallbackOutcome.importHtml(OAuthHtmlRenderer.importSuccess(jsonData, source, origin));
        } catch (Exception e) {
            LOG.errorf(e, "Import callback failed for provider=%s", provider);
            return CallbackOutcome.importHtml(OAuthHtmlRenderer.importError("Import failed", origin));
        }
    }

    private String normalizeIntent(String provider, String intent) {
        if (INTENT_PROFILE_IMPORT.equals(intent) && tokenExchangeService.isProviderEnabled(provider)) {
            return INTENT_PROFILE_IMPORT;
        }
        return "";
    }

    public record LoginOutcome(URI redirectUri, ErrorResponse error) {
        public static LoginOutcome redirect(URI redirectUri) {
            return new LoginOutcome(redirectUri, null);
        }

        public static LoginOutcome error(int status, String title, String detail) {
            return new LoginOutcome(null, new ErrorResponse(status, title, detail));
        }

        public boolean isRedirect() {
            return redirectUri != null;
        }
    }

    public record CallbackOutcome(
            String sessionCookieValue,
            OAuthHtmlRenderer.RenderedHtml importHtml,
            ErrorResponse error
    ) {
        public static CallbackOutcome sessionRedirect(String sessionCookieValue) {
            return new CallbackOutcome(sessionCookieValue, null, null);
        }

        public static CallbackOutcome importHtml(OAuthHtmlRenderer.RenderedHtml importHtml) {
            return new CallbackOutcome(null, importHtml, null);
        }

        public static CallbackOutcome error(int status, String title, String detail) {
            return new CallbackOutcome(null, null, new ErrorResponse(status, title, detail));
        }

        public boolean isSessionRedirect() {
            return sessionCookieValue != null;
        }
    }

    public record ErrorResponse(int status, String title, String detail) {}
}
