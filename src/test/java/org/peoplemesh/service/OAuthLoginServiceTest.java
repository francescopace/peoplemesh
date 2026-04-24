package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    @Mock
    SessionService sessionService;
    @Mock
    OAuthTokenExchangeService tokenExchangeService;
    @Mock
    OAuthCallbackService oAuthCallbackService;

    @InjectMocks
    OAuthLoginService service;

    @Test
    void providers_returnsLoginAndConfiguredLists() {
        when(tokenExchangeService.isProviderEnabled("microsoft")).thenReturn(false);
        when(tokenExchangeService.isLoginEnabled("google")).thenReturn(true);
        when(tokenExchangeService.isProviderEnabled("google")).thenReturn(true);
        when(tokenExchangeService.isProviderEnabled("github")).thenReturn(true);

        Map<String, Object> result = service.providers();

        assertTrue(result.containsKey("providers"));
        assertTrue(result.containsKey("configured"));
    }

    @Test
    void login_providerNotEnabled_returnsError() {
        when(tokenExchangeService.isProviderEnabled("github")).thenReturn(false);

        OAuthLoginService.LoginOutcome outcome = service.login("github", "", "http://localhost/cb");

        assertTrue(outcome.error() != null);
        assertEquals(501, outcome.error().status());
    }

    @Test
    void login_success_returnsRedirect() {
        when(tokenExchangeService.isProviderEnabled("google")).thenReturn(true);
        when(sessionService.signOAuthState("google", "")).thenReturn("state");
        when(tokenExchangeService.buildAuthorizeUri("google", "http://localhost/cb", "state"))
                .thenReturn(URI.create("https://example/auth"));

        OAuthLoginService.LoginOutcome outcome = service.login("google", "", "http://localhost/cb");

        assertTrue(outcome.isRedirect());
    }

    @Test
    void login_profileImportIntent_preservesIntentInState() {
        when(tokenExchangeService.isProviderEnabled("github")).thenReturn(true);
        when(sessionService.signOAuthState("github", "profile_import")).thenReturn("import-state");
        when(tokenExchangeService.buildAuthorizeUri("github", "http://localhost/cb", "import-state"))
                .thenReturn(URI.create("https://example/import-auth"));

        OAuthLoginService.LoginOutcome outcome = service.login("github", "profile_import", "http://localhost/cb");

        assertTrue(outcome.isRedirect());
        verify(sessionService).signOAuthState("github", "profile_import");
    }

    @Test
    void login_authorizeUriMissing_returnsError() {
        when(tokenExchangeService.isProviderEnabled("google")).thenReturn(true);
        when(sessionService.signOAuthState("google", "")).thenReturn("state");
        when(tokenExchangeService.buildAuthorizeUri("google", "http://localhost/cb", "state")).thenReturn(null);

        OAuthLoginService.LoginOutcome outcome = service.login("google", "", "http://localhost/cb");

        assertNotNull(outcome.error());
        assertEquals(501, outcome.error().status());
        assertEquals("OAuth login is not implemented for provider: google", outcome.error().detail());
    }

    @Test
    void callback_missingCodeOrState_returnsBadRequest() {
        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "", "", null, "cb", "origin");
        assertEquals(400, outcome.error().status());
    }

    @Test
    void callback_importIntent_errorReturnsImportHtml() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("profile_import");
        when(sessionService.verifyOAuthState("s", "google")).thenReturn(payload);

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", null, "s", "denied", "cb", "http://localhost");

        assertNotNull(outcome.importRedirectUri());
        assertTrue(outcome.importRedirectUri().toString().contains("#/oauth/import?"));
        assertTrue(outcome.importRedirectUri().toString().contains("provider=google"));
        assertTrue(outcome.importRedirectUri().toString().contains("error=OAuth+callback+failed"));
    }

    @Test
    void callback_errorWithoutImport_returnsBadRequest() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        when(sessionService.verifyOAuthState("state", "google")).thenReturn(payload);

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "code", "state", "access_denied", "cb", "http://localhost");

        assertNotNull(outcome.error());
        assertEquals(400, outcome.error().status());
        assertEquals("OAuth callback failed", outcome.error().detail());
    }

    @Test
    void callback_invalidState_returnsBadRequest() {
        when(sessionService.verifyOAuthState("state", "google")).thenReturn(null);

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "code", "state", null, "cb", "origin");

        assertNotNull(outcome.error());
        assertEquals(400, outcome.error().status());
        assertEquals("Invalid or expired state", outcome.error().detail());
    }

    @Test
    void callback_successfulLogin_returnsSessionCookie() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        OidcSubject subject = new OidcSubject(
                "sub", "Name", "Given", "Family", "email@test.com", null, null, null, null, null);
        OAuthCallbackService.LoginResult loginResult = new OAuthCallbackService.LoginResult(UUID.randomUUID(), "Name", false);

        when(sessionService.verifyOAuthState("state", "google")).thenReturn(payload);
        when(tokenExchangeService.exchangeAndResolveSubject("google", "code", "cb")).thenReturn(subject);
        when(oAuthCallbackService.handleLogin("google", subject)).thenReturn(loginResult);
        when(sessionService.encodeSession(loginResult.userId(), "google", "Name")).thenReturn("cookie");

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "code", "state", null, "cb", "origin");

        assertTrue(outcome.isSessionRedirect());
        assertEquals("cookie", outcome.sessionCookieValue());
    }

    @Test
    void callback_tokenExchangeFailure_returnsBadGateway() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        when(sessionService.verifyOAuthState("state", "google")).thenReturn(payload);
        when(tokenExchangeService.exchangeAndResolveSubject("google", "code", "cb"))
                .thenThrow(new IllegalStateException("provider down"));

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "code", "state", null, "cb", "origin");

        assertNotNull(outcome.error());
        assertEquals(502, outcome.error().status());
        assertEquals("Token exchange failed", outcome.error().detail());
    }

    @Test
    void callback_subjectMissing_returnsBadGateway() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        when(sessionService.verifyOAuthState("state", "google")).thenReturn(payload);
        when(tokenExchangeService.exchangeAndResolveSubject("google", "code", "cb")).thenReturn(null);

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "google", "code", "state", null, "cb", "origin");

        assertNotNull(outcome.error());
        assertEquals(502, outcome.error().status());
        assertEquals("Token exchange failed", outcome.error().detail());
    }

    @Test
    void callback_importSuccess_returnsHtml() throws Exception {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("profile_import");
        when(sessionService.verifyOAuthState("state", "github")).thenReturn(payload);

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "github", "code", "state", null, "cb", "http://localhost");

        assertNotNull(outcome.importRedirectUri());
        String redirect = outcome.importRedirectUri().toString();
        assertTrue(redirect.contains("#/oauth/import?"));
        assertTrue(redirect.contains("provider=github"));
        assertTrue(redirect.contains("code=code"));
        assertTrue(redirect.contains("state=state"));
    }

    @Test
    void finalizeImportCallback_success_returnsImportedPayload() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("profile_import");
        ProfileSchema schema = mock(ProfileSchema.class);
        when(sessionService.verifyOAuthState("state", "github")).thenReturn(payload);
        when(oAuthCallbackService.handleImport("github", "code", "cb")).thenReturn(schema);

        OAuthLoginService.ImportFinalizeOutcome outcome = service.finalizeImportCallback(
                "github", "code", "state", "cb");

        assertTrue(outcome.isSuccess());
        assertEquals("github", outcome.source());
        assertEquals(schema, outcome.imported());
        assertNull(outcome.error());
    }

    @Test
    void finalizeImportCallback_invalidIntent_returnsBadRequest() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        when(sessionService.verifyOAuthState("state", "github")).thenReturn(payload);

        OAuthLoginService.ImportFinalizeOutcome outcome = service.finalizeImportCallback(
                "github", "code", "state", "cb");

        assertNotNull(outcome.error());
        assertEquals(400, outcome.error().status());
    }

    @Test
    void finalizeImportCallback_importFailure_returnsBadGateway() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("profile_import");
        when(sessionService.verifyOAuthState("state", "github")).thenReturn(payload);
        when(oAuthCallbackService.handleImport("github", "code", "cb"))
                .thenThrow(new RuntimeException("import failed"));

        OAuthLoginService.ImportFinalizeOutcome outcome = service.finalizeImportCallback(
                "github", "code", "state", "cb");

        assertNotNull(outcome.error());
        assertEquals(502, outcome.error().status());
        assertEquals("Import failed", outcome.error().detail());
    }
}
