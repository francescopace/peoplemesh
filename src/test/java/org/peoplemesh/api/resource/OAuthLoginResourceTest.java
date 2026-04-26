package org.peoplemesh.api.resource;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.AuthIdentityResponse;
import org.peoplemesh.service.AuthIdentityService;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.service.OAuthLoginService;
import org.peoplemesh.service.SessionService;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginResourceTest {

    @Mock
    UriInfo uriInfo;
    @Mock
    OAuthLoginService oAuthLoginService;
    @Mock
    SessionService sessionService;
    @Mock
    SecurityIdentity identity;
    @Mock
    AuthIdentityService authIdentityService;
    @Mock
    AppConfig appConfig;
    @Mock
    AppConfig.FrontendConfig frontendConfig;

    @InjectMocks
    OAuthLoginResource resource;

    @Test
    void login_whenServiceReturnsRedirect_returnsTemporaryRedirect() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(oAuthLoginService.login(eq("google"), eq("profile_import"), anyString()))
                .thenReturn(OAuthLoginService.LoginOutcome.redirect(URI.create("https://oauth.example/authorize")));

        Response response = resource.login("google", "profile_import");

        assertEquals(307, response.getStatus());
        assertEquals("https://oauth.example/authorize", response.getLocation().toString());
        verify(oAuthLoginService).login(
                eq("google"),
                eq("profile_import"),
                eq("https://api.peoplemesh.test/api/v1/auth/callback/google"));
    }

    @Test
    void login_whenServiceReturnsError_returnsProblemDetail() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(oAuthLoginService.login(eq("github"), eq(""), anyString()))
                .thenReturn(OAuthLoginService.LoginOutcome.error(501, "Not Implemented", "Provider not configured"));

        Response response = resource.login("github", "");

        assertEquals(501, response.getStatus());
        ProblemDetail detail = assertInstanceOf(ProblemDetail.class, response.getEntity());
        assertEquals("Not Implemented", detail.title());
        assertEquals("Provider not configured", detail.detail());
    }

    @Test
    void callback_whenImportRedirectOutcome_returnsTemporaryRedirect() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(appConfig.frontend()).thenReturn(frontendConfig);
        when(frontendConfig.origin()).thenReturn(Optional.of("https://app.peoplemesh.test"));
        when(oAuthLoginService.callback(eq("google"), eq("code"), eq("state"), eq(""),
                eq("https://api.peoplemesh.test/api/v1/auth/callback/google"), eq("https://app.peoplemesh.test")))
                .thenReturn(OAuthLoginService.CallbackOutcome.importRedirect(
                        URI.create("https://app.peoplemesh.test/#/oauth/import?provider=google&code=code&state=state")));

        Response response = resource.callback("google", "code", "state", "", "");

        assertEquals(307, response.getStatus());
        assertEquals("https://app.peoplemesh.test/#/oauth/import?provider=google&code=code&state=state",
                response.getLocation().toString());
    }

    @Test
    void callback_whenSessionRedirect_setsCookieAndSearchRedirect() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://api.peoplemesh.test/api/v1/auth/callback/google"));
        when(appConfig.frontend()).thenReturn(frontendConfig);
        when(frontendConfig.origin()).thenReturn(Optional.empty());
        when(sessionService.sessionMaxAgeSeconds()).thenReturn(3600);
        when(oAuthLoginService.callback(eq("google"), eq("code"), eq("state"), eq(null),
                eq("https://api.peoplemesh.test/api/v1/auth/callback/google"), eq("https://api.peoplemesh.test")))
                .thenReturn(OAuthLoginService.CallbackOutcome.sessionRedirect("signed-cookie"));

        Response response = resource.callback("google", "code", "state", null, null);

        assertEquals(307, response.getStatus());
        assertTrue(response.getLocation().toString().endsWith("#/search"));
        NewCookie cookie = response.getCookies().get(SessionService.COOKIE_NAME);
        assertNotNull(cookie);
        assertEquals("signed-cookie", cookie.getValue());
        assertEquals(3600, cookie.getMaxAge());
        assertTrue(cookie.isSecure());
    }

    @Test
    void callback_whenErrorOutcome_returnsProblemDetail() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/"));
        when(appConfig.frontend()).thenReturn(frontendConfig);
        when(frontendConfig.origin()).thenReturn(Optional.of("javascript:alert(1)"));
        when(oAuthLoginService.callback(eq("github"), eq(null), eq(null), eq("access_denied"),
                eq("http://localhost:8080/api/v1/auth/callback/github"), eq("http://localhost:8080")))
                .thenReturn(OAuthLoginService.CallbackOutcome.error(400, "Bad Request", "OAuth callback failed"));

        Response response = resource.callback("github", null, null, "access_denied", null);

        assertEquals(400, response.getStatus());
        ProblemDetail detail = assertInstanceOf(ProblemDetail.class, response.getEntity());
        assertEquals("Bad Request", detail.title());
        assertEquals("OAuth callback failed", detail.detail());
    }

    @Test
    void importFinalize_whenSuccessful_returnsImportedPayload() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        ProfileSchema schema = mock(ProfileSchema.class);
        when(oAuthLoginService.finalizeImportCallback(
                eq("github"),
                eq("code"),
                eq("state"),
                eq("https://api.peoplemesh.test/api/v1/auth/callback/github")
        )).thenReturn(OAuthLoginService.ImportFinalizeOutcome.success(schema, "github"));

        Response response = resource.importFinalize("github", "code", "state");

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("imported", schema, "source", "github"), response.getEntity());
    }

    @Test
    void importFinalize_whenServiceReturnsError_returnsProblemDetail() {
        when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));
        when(oAuthLoginService.finalizeImportCallback(
                eq("github"),
                eq(""),
                eq("state"),
                eq("https://api.peoplemesh.test/api/v1/auth/callback/github")
        )).thenReturn(OAuthLoginService.ImportFinalizeOutcome.error(400, "Bad Request", "Missing code or state"));

        Response response = resource.importFinalize("github", "", "state");

        assertEquals(400, response.getStatus());
        ProblemDetail detail = assertInstanceOf(ProblemDetail.class, response.getEntity());
        assertEquals("Bad Request", detail.title());
        assertEquals("Missing code or state", detail.detail());
    }

    @Test
    void logout_clearsCookieAndKeepsSecureFlagFromRequest() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost:8080/api/v1/auth/logout"));

        Response response = resource.logout();

        assertEquals(204, response.getStatus());
        NewCookie cookie = response.getCookies().get(SessionService.COOKIE_NAME);
        assertNotNull(cookie);
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge());
        assertEquals("/", cookie.getPath());
        assertEquals(false, cookie.isSecure());
    }

    @Test
    void getIdentity_whenIdentityResolved_returnsPayload() {
        AuthIdentityResponse payload = new AuthIdentityResponse(
                UUID.randomUUID(),
                "google",
                new AuthIdentityResponse.EntitlementsInfo(true),
                "Alice",
                "https://cdn.example/avatar.png"
        );
        when(authIdentityService.resolveCurrentIdentity(identity)).thenReturn(Optional.of(payload));

        Response response = resource.getIdentity();

        assertEquals(200, response.getStatus());
        assertEquals(payload, response.getEntity());
    }

    @Test
    void getIdentity_whenIdentityMissing_returns204() {
        when(authIdentityService.resolveCurrentIdentity(identity)).thenReturn(Optional.empty());

        Response response = resource.getIdentity();

        assertEquals(204, response.getStatus());
    }
}
