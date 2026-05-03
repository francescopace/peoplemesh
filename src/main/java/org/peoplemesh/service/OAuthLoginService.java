package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.AuthProvidersDto;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class OAuthLoginService {

    private static final Logger LOG = Logger.getLogger(OAuthLoginService.class);
    public static final String INTENT_PROFILE_IMPORT = "profile_import";
    public static final String INTENT_MCP_AUTH = "mcp_auth";
    public static final String INTENT_MCP_OAUTH = "mcp_oauth";
    private static final List<String> PROVIDER_ORDER = List.of("google", "microsoft", "github");

    @Inject
    SessionService sessionService;

    @Inject
    OAuthTokenExchangeService tokenExchangeService;

    @Inject
    OAuthCallbackService oAuthCallbackService;

    @Inject
    McpOAuthService mcpOAuthService;

    public AuthProvidersDto providers() {
        List<String> loginProviders = PROVIDER_ORDER.stream()
                .filter(tokenExchangeService::isLoginEnabled)
                .toList();
        List<String> importProviders = PROVIDER_ORDER.stream()
                .filter(tokenExchangeService::isProviderEnabled)
                .filter(provider -> !tokenExchangeService.isLoginEnabled(provider))
                .toList();
        return new AuthProvidersDto(loginProviders, importProviders);
    }

    public LoginOutcome login(String provider, String intent, String callbackUri) {
        return login(provider, intent, callbackUri, null);
    }

    public LoginOutcome login(String provider, String intent, String callbackUri, String context) {
        if (!tokenExchangeService.isProviderEnabled(provider)) {
            return LoginOutcome.error(501, "Not Implemented",
                    "OAuth login is not configured for provider: " + provider);
        }
        String normalizedIntent = normalizeIntent(provider, intent);
        String state = sessionService.signOAuthState(provider, normalizedIntent, context);
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
                return handleImportRedirectCallback(provider, code, state, origin, "OAuth callback failed");
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
            return handleImportRedirectCallback(provider, code, state, origin, null);
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
        URI sessionRedirectUri = resolveSessionRedirect(statePayload, callbackUri, result.userId(), provider, result.displayName());
        return CallbackOutcome.sessionRedirect(cookieValue, sessionRedirectUri);
    }

    private CallbackOutcome handleImportRedirectCallback(
            String provider,
            String code,
            String state,
            String origin,
            String callbackError
    ) {
        URI redirectUri = buildImportFrontendUri(origin, provider, code, state, callbackError);
        if (redirectUri == null) {
            return CallbackOutcome.error(500, "Internal Server Error", "Import redirect failed");
        }
        return CallbackOutcome.importRedirect(redirectUri);
    }

    public ImportFinalizeOutcome finalizeImportCallback(
            String provider,
            String code,
            String state,
            String callbackUri
    ) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ImportFinalizeOutcome.error(400, "Bad Request", "Missing code or state");
        }
        SessionService.OAuthStatePayload statePayload = sessionService.verifyOAuthState(state, provider);
        if (statePayload == null) {
            return ImportFinalizeOutcome.error(400, "Bad Request", "Invalid or expired state");
        }
        if (!INTENT_PROFILE_IMPORT.equals(statePayload.intent())) {
            return ImportFinalizeOutcome.error(400, "Bad Request", "Invalid OAuth intent");
        }
        try {
            ProfileSchema importSchema = oAuthCallbackService.handleImport(provider, code, callbackUri);
            return ImportFinalizeOutcome.success(importSchema, sourceForProvider(provider));
        } catch (Exception e) {
            LOG.errorf(e, "Import callback failed for provider=%s", provider);
            return ImportFinalizeOutcome.error(502, "Bad Gateway", "Import failed");
        }
    }

    private URI buildImportFrontendUri(
            String origin,
            String provider,
            String code,
            String state,
            String error
    ) {
        if (origin == null || origin.isBlank() || provider == null || provider.isBlank()) {
            return null;
        }
        StringBuilder fragment = new StringBuilder("/oauth/import?provider=")
                .append(urlEncode(provider));
        if (code != null && !code.isBlank()) {
            fragment.append("&code=").append(urlEncode(code));
        }
        if (state != null && !state.isBlank()) {
            fragment.append("&state=").append(urlEncode(state));
        }
        if (error != null && !error.isBlank()) {
            fragment.append("&error=").append(urlEncode(error));
        }
        String safeOrigin = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        try {
            return URI.create(safeOrigin + "/#" + fragment);
        } catch (IllegalArgumentException e) {
            LOG.errorf(e, "Invalid frontend origin for import redirect: %s", origin);
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sourceForProvider(String provider) {
        return "github".equals(provider) ? "github" : provider;
    }

    private String normalizeIntent(String provider, String intent) {
        if (INTENT_PROFILE_IMPORT.equals(intent) && tokenExchangeService.isProviderEnabled(provider)) {
            return INTENT_PROFILE_IMPORT;
        }
        if (INTENT_MCP_AUTH.equals(intent) && tokenExchangeService.isLoginEnabled(provider)) {
            return INTENT_MCP_AUTH;
        }
        if (INTENT_MCP_OAUTH.equals(intent) && tokenExchangeService.isLoginEnabled(provider)) {
            return INTENT_MCP_OAUTH;
        }
        return "";
    }

    private URI resolveSessionRedirect(
            SessionService.OAuthStatePayload statePayload,
            String callbackUri,
            java.util.UUID userId,
            String provider,
            String displayName
    ) {
        if (INTENT_MCP_AUTH.equals(statePayload.intent())) {
            return buildMcpCompleteUri(callbackUri);
        }
        if (INTENT_MCP_OAUTH.equals(statePayload.intent())) {
            String requestId = statePayload.context();
            if (requestId == null || requestId.isBlank()) {
                LOG.warn("Missing OAuth authorization request context for MCP OAuth intent");
                return null;
            }
            return mcpOAuthService.completeAuthorization(requestId, userId, provider, displayName)
                    .map(URI::create)
                    .orElse(null);
        }
        return null;
    }

    private URI buildMcpCompleteUri(String callbackUri) {
        try {
            URI callback = URI.create(callbackUri).normalize();
            return callback.resolve("/api/v1/auth/mcp/complete");
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid callback URI for MCP completion redirect: %s", callbackUri);
            return null;
        }
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
            URI sessionRedirectUri,
            URI importRedirectUri,
            ErrorResponse error
    ) {
        public static CallbackOutcome sessionRedirect(String sessionCookieValue, URI sessionRedirectUri) {
            return new CallbackOutcome(sessionCookieValue, sessionRedirectUri, null, null);
        }

        public static CallbackOutcome importRedirect(URI importRedirectUri) {
            return new CallbackOutcome(null, null, importRedirectUri, null);
        }

        public static CallbackOutcome error(int status, String title, String detail) {
            return new CallbackOutcome(null, null, null, new ErrorResponse(status, title, detail));
        }

        public boolean isSessionRedirect() {
            return sessionCookieValue != null;
        }
    }

    public record ImportFinalizeOutcome(ProfileSchema imported, String source, ErrorResponse error) {
        public static ImportFinalizeOutcome success(ProfileSchema imported, String source) {
            return new ImportFinalizeOutcome(imported, source, null);
        }

        public static ImportFinalizeOutcome error(int status, String title, String detail) {
            return new ImportFinalizeOutcome(null, null, new ErrorResponse(status, title, detail));
        }

        public boolean isSuccess() {
            return imported != null && error == null;
        }
    }

    public record ErrorResponse(int status, String title, String detail) {}
}
