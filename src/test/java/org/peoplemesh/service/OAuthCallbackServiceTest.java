package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.service.OAuthTokenExchangeService.GitHubEnrichedResult;
import org.peoplemesh.service.OAuthTokenExchangeService.OidcSubject;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthCallbackServiceTest {

    @Mock OAuthTokenExchangeService tokenExchangeService;
    @Mock ProfileService profileService;
    @Mock ConsentService consentService;
    @Mock AppConfig appConfig;

    @InjectMocks
    OAuthCallbackService service;

    @BeforeEach
    void setUp() {
        AppConfig.EntitlementsConfig ent = mock(AppConfig.EntitlementsConfig.class);
        lenient().when(appConfig.entitlements()).thenReturn(ent);
        lenient().when(ent.isAdmin()).thenReturn(Optional.empty());
    }

    @Test
    void handleImport_github_delegatesToExchangeGitHubEnriched() {
        GitHubEnrichedResult enriched = mock(GitHubEnrichedResult.class);
        when(tokenExchangeService.exchangeGitHubEnriched("code", "redirect"))
                .thenReturn(enriched);

        try (var parserMock = mockStatic(OAuthProfileParser.class)) {
            ProfileSchema schema = mock(ProfileSchema.class);
            parserMock.when(() -> OAuthProfileParser.buildEnrichedGitHubSchema(enriched))
                    .thenReturn(schema);

            ProfileSchema result = service.handleImport("github", "code", "redirect");
            assertSame(schema, result);
        }
    }

    @Test
    void handleImport_oidcProvider_delegatesToExchangeAndResolveSubject() {
        OidcSubject subject = new OidcSubject("sub", null, null, null, "test@test.com", null, null, null, null, null);
        when(tokenExchangeService.exchangeAndResolveSubject("google", "code", "redirect"))
                .thenReturn(subject);

        try (var parserMock = mockStatic(OAuthProfileParser.class)) {
            ProfileSchema schema = mock(ProfileSchema.class);
            parserMock.when(() -> OAuthProfileParser.buildImportSchema("google", subject))
                    .thenReturn(schema);

            ProfileSchema result = service.handleImport("google", "code", "redirect");
            assertSame(schema, result);
        }
    }

    @Test
    void handleImport_tokenExchangeReturnsNull_throwsIllegalState() {
        when(tokenExchangeService.exchangeAndResolveSubject(eq("google"), anyString(), anyString()))
                .thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.handleImport("google", "code", "redirect"));
    }

    @Test
    void handleImport_githubExchangeReturnsNull_throwsIllegalState() {
        when(tokenExchangeService.exchangeGitHubEnriched(anyString(), anyString()))
                .thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.handleImport("github", "code", "redirect"));
    }

    @Test
    void handleImport_exceptionWrapped_throwsIllegalState() {
        when(tokenExchangeService.exchangeAndResolveSubject(eq("google"), anyString(), anyString()))
                .thenThrow(new RuntimeException("network error"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.handleImport("google", "code", "redirect"));
        assertTrue(ex.getMessage().contains("Token exchange failed"));
    }

    @Test
    void defaultConsentScopes_containsExpectedValues() {
        assertTrue(OAuthCallbackService.DEFAULT_CONSENT_SCOPES.contains("professional_matching"));
        assertTrue(OAuthCallbackService.DEFAULT_CONSENT_SCOPES.contains("embedding_processing"));
        assertEquals(2, OAuthCallbackService.DEFAULT_CONSENT_SCOPES.size());
    }

    @Test
    void normalizeEmail_blankAndInvalid() throws Exception {
        var method = OAuthCallbackService.class.getDeclaredMethod("normalizeEmail", String.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, (String) null));
        assertNull(method.invoke(null, "  "));
        assertNull(method.invoke(null, "no-at-sign"));
        assertEquals("user@example.com", method.invoke(null, "  user@example.com  "));
    }
}
