package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    @Mock
    SessionService sessionService;
    @Mock
    OAuthTokenExchangeService tokenExchangeService;
    @Mock
    OAuthCallbackService oAuthCallbackService;
    @Mock
    ObjectMapper objectMapper;

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

        assertNotNull(outcome.importHtml());
    }

    @Test
    void callback_successfulLogin_returnsSessionCookie() {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("");
        OAuthTokenExchangeService.OidcSubject subject = new OAuthTokenExchangeService.OidcSubject(
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
    void callback_importSuccess_returnsHtml() throws Exception {
        SessionService.OAuthStatePayload payload = new SessionService.OAuthStatePayload("profile_import");
        ProfileSchema schema = mock(ProfileSchema.class);
        when(sessionService.verifyOAuthState("state", "github")).thenReturn(payload);
        when(oAuthCallbackService.handleImport("github", "code", "cb")).thenReturn(schema);
        when(objectMapper.writeValueAsString(schema)).thenReturn("{\"ok\":true}");

        OAuthLoginService.CallbackOutcome outcome = service.callback(
                "github", "code", "state", null, "cb", "http://localhost");

        assertNotNull(outcome.importHtml());
    }
}
