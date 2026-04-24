package org.peoplemesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthTokenExchangeServiceTest {

    private OAuthTokenExchangeService service;

    @BeforeEach
    void setUp() {
        service = new OAuthTokenExchangeService();
    }

    @Test
    void isConfigured_empty_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("", "")));
    }

    @Test
    void isConfigured_blank_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("   ", "   ")));
    }

    @Test
    void isConfigured_missingSecret_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("client-id", "")));
    }

    @Test
    void isConfigured_validValue_returnsTrue() {
        assertTrue(OAuthTokenExchangeService.isConfigured(stubCreds("real-client-id", "real-secret")));
    }

    @Test
    void isProviderEnabled_google_whenConfigured_returnsTrue() throws Exception {
        setAppConfig(stubProviders("google", "id", "secret"));
        assertTrue(service.isProviderEnabled("google"));
    }

    @Test
    void isProviderEnabled_unknown_returnsFalse() throws Exception {
        setAppConfig(stubProviders(null, null, null));
        assertFalse(service.isProviderEnabled("unknown"));
    }

    @Test
    void isLoginEnabled_loginProvider_whenConfigured_returnsTrue() throws Exception {
        setAppConfig(stubProviders("google", "id", "secret"));
        assertTrue(service.isLoginEnabled("google"));
    }

    @Test
    void isLoginEnabled_importOnlyProvider_returnsFalse() throws Exception {
        setAppConfig(stubProviders("github", "id", "secret"));
        assertFalse(service.isLoginEnabled("github"));
        assertTrue(service.isProviderEnabled("github"));
    }

    @Test
    void buildAuthorizeUri_google_returnsValidUri() throws Exception {
        setAppConfig(stubProviders("google", "my-client", "my-secret"));
        URI uri = service.buildAuthorizeUri("google", "https://app/cb", "state-xyz");
        assertNotNull(uri);
        assertEquals("accounts.google.com", uri.getHost());
        String q = uri.getRawQuery();
        assertTrue(q.contains("client_id="));
        assertTrue(q.contains("redirect_uri="));
        assertTrue(q.contains("state="));
    }

    @Test
    void buildAuthorizeUri_microsoftAndGithub_returnProviderUris() throws Exception {
        setAppConfig(stubProviders("microsoft", "my-client", "my-secret"));
        URI ms = service.buildAuthorizeUri("microsoft", "https://app/cb", "state-xyz");
        assertNotNull(ms);
        assertEquals("login.microsoftonline.com", ms.getHost());
        assertTrue(ms.getRawQuery().contains("response_mode=query"));

        setAppConfig(stubProviders("github", "my-client", "my-secret"));
        URI gh = service.buildAuthorizeUri("github", "https://app/cb", "state-xyz");
        assertNotNull(gh);
        assertEquals("github.com", gh.getHost());
        assertTrue(gh.getRawQuery().contains("scope=user%3Aemail"));
    }

    @Test
    void buildAuthorizeUri_unconfigured_returnsNull() throws Exception {
        setAppConfig(stubProviders("google", "", ""));
        assertNull(service.buildAuthorizeUri("google", "https://app/cb", "st"));
    }

    @Test
    void buildAuthorizeUri_unknownProvider_returnsNull() throws Exception {
        setAppConfig(stubProviders("google", "id", "secret"));
        assertNull(service.buildAuthorizeUri("unknown", "https://app/cb", "st"));
    }

    @Test
    void text_nullNode_returnsNull() {
        assertNull(OAuthTokenExchangeService.text(null, "f"));
    }

    @Test
    void text_missingField_returnsNull() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode().put("other", "x");
        assertNull(OAuthTokenExchangeService.text(node, "name"));
    }

    @Test
    void text_nonTextField_returnsNull() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode().put("n", 42);
        assertNull(OAuthTokenExchangeService.text(node, "n"));
    }

    @Test
    void text_validTextField_returnsValue() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode().put("sub", "12345").put("name", "Test User");
        assertEquals("12345", OAuthTokenExchangeService.text(node, "sub"));
        assertEquals("Test User", OAuthTokenExchangeService.text(node, "name"));
    }

    @Test
    void text_nullOrBlankFieldName_returnsNull() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.createObjectNode().put("a", "b");
        assertNull(OAuthTokenExchangeService.text(node, null));
        assertNull(OAuthTokenExchangeService.text(node, ""));
        assertNull(OAuthTokenExchangeService.text(node, "   "));
    }

    @Test
    void tokenBody_withGrantType_appendsAuthorizationCodeGrant() throws Exception {
        Method m = instanceMethod("tokenBody", String.class, String.class, String.class, String.class, boolean.class);
        String body = (String) m.invoke(service, "c1", "id1", "sec1", "https://cb", true);
        assertTrue(body.contains("grant_type=authorization_code"));
        assertTrue(body.contains("code="));
        assertTrue(body.contains("client_id="));
        assertTrue(body.contains("client_secret="));
        assertTrue(body.contains("redirect_uri="));
    }

    @Test
    void tokenBody_withoutGrantType_omitsGrantType() throws Exception {
        Method m = instanceMethod("tokenBody", String.class, String.class, String.class, String.class, boolean.class);
        String body = (String) m.invoke(service, "c1", "id1", "sec1", "https://cb", false);
        assertFalse(body.contains("grant_type"));
    }

    @Test
    void enc_urlEncodesUtf8() throws Exception {
        Method m = staticMethod("enc", String.class);
        assertEquals("a+b", m.invoke(null, "a b"));
        assertEquals("hello%40world", m.invoke(null, "hello@world"));
    }

    @Test
    void isConfigured_emptyString_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("", "secret")));
    }

    @Test
    void isConfigured_onlyNewlines_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("\n\r", "\n\r")));
    }

    @Test
    void isConfigured_noneSentinel_returnsFalse() {
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("none", "secret")));
        assertFalse(OAuthTokenExchangeService.isConfigured(stubCreds("client", "none")));
    }

    @Test
    void exchangeAndResolveSubject_google_successReturnsSubject() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("google", "id", "secret");
        fake.enqueuePost("https://oauth2.googleapis.com/token", "{\"access_token\":\"tok-g\"}");
        fake.enqueueGet("https://www.googleapis.com/oauth2/v3/userinfo",
                "{\"sub\":\"sub-g\",\"name\":\"Google User\",\"given_name\":\"Google\",\"family_name\":\"User\",\"email\":\"g@example.com\",\"locale\":\"en\",\"picture\":\"pic\",\"hd\":\"acme.com\"}");

        OidcSubject s =
                fake.exchangeAndResolveSubject("google", "code", "https://cb");

        assertNotNull(s);
        assertEquals("sub-g", s.subject());
        assertEquals("g@example.com", s.email());
        assertEquals("acme.com", s.hostedDomain());
    }

    @Test
    void exchangeAndResolveSubject_microsoft_successReturnsSubject() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("microsoft", "id", "secret");
        fake.enqueuePost("https://login.microsoftonline.com/common/oauth2/v2.0/token", "{\"access_token\":\"tok-ms\"}");
        fake.enqueueGet("https://graph.microsoft.com/oidc/userinfo",
                "{\"sub\":\"sub-ms\",\"name\":\"Ms User\",\"given_name\":\"Ms\",\"family_name\":\"User\",\"email\":\"m@example.com\",\"picture\":\"pic\"}");

        OidcSubject s = fake.exchangeAndResolveSubject("microsoft", "code", "https://cb");

        assertNotNull(s);
        assertEquals("sub-ms", s.subject());
        assertEquals("m@example.com", s.email());
    }

    @Test
    void exchangeAndResolveSubject_microsoft_missingAccessTokenReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("microsoft", "id", "secret");
        fake.enqueuePost("https://login.microsoftonline.com/common/oauth2/v2.0/token", "{\"token_type\":\"Bearer\"}");

        assertNull(fake.exchangeAndResolveSubject("microsoft", "code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_google_unconfiguredProviderReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("google", "", "");

        assertNull(fake.exchangeAndResolveSubject("google", "code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_google_missingSubReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("google", "id", "secret");
        fake.enqueuePost("https://oauth2.googleapis.com/token", "{\"access_token\":\"tok-g\"}");
        fake.enqueueGet("https://www.googleapis.com/oauth2/v3/userinfo",
                "{\"name\":\"Google User\",\"email\":\"g@example.com\"}");

        assertNull(fake.exchangeAndResolveSubject("google", "code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_microsoft_missingSubReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("microsoft", "id", "secret");
        fake.enqueuePost("https://login.microsoftonline.com/common/oauth2/v2.0/token", "{\"access_token\":\"tok-ms\"}");
        fake.enqueueGet("https://graph.microsoft.com/oidc/userinfo",
                "{\"name\":\"Ms User\",\"email\":\"m@example.com\"}");

        assertNull(fake.exchangeAndResolveSubject("microsoft", "code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_github_fallsBackToPrimaryEmail() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user", "{\"id\":42,\"name\":\"Octo\",\"bio\":\"builder\",\"avatar_url\":\"a\"}");
        fake.enqueueGet("https://api.github.com/user/emails",
                "[{\"email\":\"secondary@example.com\",\"primary\":false,\"verified\":true}," +
                        "{\"email\":\"primary@example.com\",\"primary\":true,\"verified\":true}]");

        OidcSubject s =
                fake.exchangeAndResolveSubject("github", "code", "https://cb");

        assertNotNull(s);
        assertEquals("42", s.subject());
        assertEquals("primary@example.com", s.email());
    }

    @Test
    void exchangeGitHubEnriched_collectsLanguagesAndRepoLabels() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user",
                "{\"id\":7,\"login\":\"octo\",\"name\":\"Octo\",\"email\":\"octo@example.com\",\"bio\":\"bio\",\"avatar_url\":\"pic\",\"company\":\"ACME\"}");
        fake.enqueueGet("https://api.github.com/user/repos?sort=pushed&per_page=20&type=owner",
                "[" +
                        "{\"fork\":false,\"full_name\":\"octo/repo1\",\"topics\":[\"java\",\"quarkus\"]}," +
                        "{\"fork\":true,\"full_name\":\"octo/forked\",\"topics\":[\"ignored\"]}," +
                        "{\"fork\":false,\"name\":\"repo2\",\"topics\":[\"backend\",\"java\"]}" +
                        "]");
        fake.enqueueGet("https://api.github.com/repos/octo/repo1/languages", "{\"Java\":100,\"Kotlin\":50}");
        fake.enqueueGet("https://api.github.com/repos/octo/repo2/languages", "{\"Java\":20,\"Go\":80}");

        GitHubEnrichedResult result =
                fake.exchangeGitHubEnriched("code", "https://cb");

        assertNotNull(result);
        assertNotNull(result.subject());
        assertEquals(List.of("Java", "Go", "Kotlin"), result.languages());
        assertTrue(result.repoLabels().contains("java"));
        assertTrue(result.repoLabels().contains("quarkus"));
        assertTrue(result.repoLabels().contains("backend"));
        assertFalse(result.repoLabels().contains("ignored"));
    }

    @Test
    void exchangeAndResolveSubject_unknownProvider_returnsNull() {
        assertNull(service.exchangeAndResolveSubject("unknown", "code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_wrapsCheckedException() {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("google", "id", "secret");
        fake.throwOnPostChecked("https://oauth2.googleapis.com/token", new Exception("io down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fake.exchangeAndResolveSubject("google", "code", "https://cb"));
        assertEquals("Token exchange failed for provider: google", ex.getMessage());
        assertNotNull(ex.getCause());
        assertEquals("io down", ex.getCause().getMessage());
    }

    @Test
    void exchangeAndResolveSubject_rethrowsRuntimeException() {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("google", "id", "secret");
        fake.throwOnPost("https://oauth2.googleapis.com/token", new RuntimeException("provider down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fake.exchangeAndResolveSubject("google", "code", "https://cb"));
        assertEquals("provider down", ex.getMessage());
    }

    @Test
    void exchangeGitHubEnriched_repoFetchFailure_returnsSubjectWithEmptyEnrichment() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user",
                "{\"id\":9,\"login\":\"octo\",\"name\":\"Octo\",\"email\":\"octo@example.com\",\"avatar_url\":\"pic\"}");
        fake.throwOnGet("https://api.github.com/user/repos?sort=pushed&per_page=20&type=owner",
                new RuntimeException("repos down"));

        GitHubEnrichedResult result =
                fake.exchangeGitHubEnriched("code", "https://cb");

        assertNotNull(result);
        assertNotNull(result.subject());
        assertTrue(result.languages().isEmpty());
        assertTrue(result.repoLabels().isEmpty());
    }

    @Test
    void exchangeGitHubEnriched_languageFetchFailure_skipsOnlyFailedRepo() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user",
                "{\"id\":77,\"login\":\"octo\",\"name\":\"Octo\",\"email\":\"octo@example.com\"}");
        fake.enqueueGet("https://api.github.com/user/repos?sort=pushed&per_page=20&type=owner",
                "[" +
                        "{\"fork\":false,\"full_name\":\"octo/repo1\",\"topics\":[\"java\"]}," +
                        "{\"fork\":false,\"full_name\":\"octo/repo2\",\"topics\":[\"go\"]}" +
                        "]");
        fake.throwOnGet("https://api.github.com/repos/octo/repo1/languages", new RuntimeException("lang down"));
        fake.enqueueGet("https://api.github.com/repos/octo/repo2/languages", "{\"Go\":80}");

        GitHubEnrichedResult result = fake.exchangeGitHubEnriched("code", "https://cb");

        assertNotNull(result);
        assertEquals(List.of("Go"), result.languages());
        assertTrue(result.repoLabels().contains("java"));
        assertTrue(result.repoLabels().contains("go"));
    }

    @Test
    void exchangeGitHubEnriched_wrapsCheckedException() {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.throwOnPostChecked("https://github.com/login/oauth/access_token", new Exception("github down"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fake.exchangeGitHubEnriched("code", "https://cb"));
        assertEquals("Token exchange failed for provider: github", ex.getMessage());
        assertNotNull(ex.getCause());
        assertEquals("github down", ex.getCause().getMessage());
    }

    @Test
    void exchangeGitHubEnriched_rethrowsRuntimeException() {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.throwOnPost("https://github.com/login/oauth/access_token", new RuntimeException("boom"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fake.exchangeGitHubEnriched("code", "https://cb"));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void exchangeAndResolveSubject_github_primaryEmailMissing_returnsNullEmail() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user", "{\"id\":42,\"name\":\"Octo\"}");
        fake.enqueueGet("https://api.github.com/user/emails",
                "[{\"email\":\"secondary@example.com\",\"primary\":false,\"verified\":true}]");

        OidcSubject s = fake.exchangeAndResolveSubject("github", "code", "https://cb");

        assertNotNull(s);
        assertNull(s.email());
    }

    @Test
    void exchangeAndResolveSubject_github_missingIdReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user", "{\"name\":\"Octo\"}");

        assertNull(fake.exchangeAndResolveSubject("github", "code", "https://cb"));
    }

    @Test
    void exchangeGitHubEnriched_unconfiguredProviderReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "", "");

        assertNull(fake.exchangeGitHubEnriched("code", "https://cb"));
    }

    @Test
    void exchangeGitHubEnriched_missingIdReturnsNull() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user",
                "{\"login\":\"octo\",\"name\":\"Octo\",\"email\":\"octo@example.com\"}");

        assertNull(fake.exchangeGitHubEnriched("code", "https://cb"));
    }

    @Test
    void exchangeAndResolveSubject_github_emailsEndpointNotArray_returnsNullEmail() throws Exception {
        FakeOAuthTokenExchangeService fake = new FakeOAuthTokenExchangeService();
        fake.objectMapper = new ObjectMapper();
        fake.appConfig = stubProviders("github", "id", "secret");
        fake.enqueuePost("https://github.com/login/oauth/access_token", "{\"access_token\":\"tok-gh\"}");
        fake.enqueueGet("https://api.github.com/user", "{\"id\":42,\"name\":\"Octo\"}");
        fake.enqueueGet("https://api.github.com/user/emails", "{\"email\":\"not-array\"}");

        OidcSubject s = fake.exchangeAndResolveSubject("github", "code", "https://cb");

        assertNotNull(s);
        assertNull(s.email());
    }

    @Test
    void httpGetResult_helpersWorkAsExpected() {
        OAuthTokenExchangeService.HttpGetResult ok =
                new OAuthTokenExchangeService.HttpGetResult(200, "abc".getBytes(StandardCharsets.UTF_8));
        OAuthTokenExchangeService.HttpGetResult fail =
                new OAuthTokenExchangeService.HttpGetResult(500, new byte[0]);
        OAuthTokenExchangeService.HttpGetResult nullBody =
                new OAuthTokenExchangeService.HttpGetResult(200, null);

        assertTrue(ok.isSuccess());
        assertEquals("abc", ok.bodyAsText());
        assertFalse(fail.isSuccess());
        assertEquals("<empty>", fail.bodyAsText());
        assertEquals("<empty>", nullBody.bodyAsText());
    }

    @Test
    void httpGetBearer_returnsBodyOnlyForSuccess() throws Exception {
        HttpGetBearerProxyService proxy = new HttpGetBearerProxyService();
        proxy.next = new OAuthTokenExchangeService.HttpGetResult(200, "ok".getBytes(StandardCharsets.UTF_8));
        assertEquals("ok", new String(proxy.httpGetBearer("http://x", "tok"), StandardCharsets.UTF_8));

        proxy.next = new OAuthTokenExchangeService.HttpGetResult(500, "err".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, proxy.httpGetBearer("http://x", "tok").length);
    }

    // === Helpers ===

    private void setAppConfig(AppConfig config) throws Exception {
        Field f = OAuthTokenExchangeService.class.getDeclaredField("appConfig");
        f.setAccessible(true);
        f.set(service, config);
    }

    private static Method staticMethod(String name, Class<?>... params) throws Exception {
        Method m = OAuthTokenExchangeService.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static Method instanceMethod(String name, Class<?>... params) throws Exception {
        Method m = OAuthTokenExchangeService.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static AppConfig.OidcProviderCreds stubCreds(String clientId, String clientSecret) {
        return new AppConfig.OidcProviderCreds() {
            @Override public String clientId() { return clientId; }
            @Override public String clientSecret() { return clientSecret; }
        };
    }

    private static AppConfig stubProviders(String enabledProvider, String id, String secret) {
        AppConfig.OidcProviderCreds enabled = stubCreds(id != null ? id : "", secret != null ? secret : "");
        AppConfig.OidcProviderCreds disabled = stubCreds("", "");
        AppConfig.OidcProviders providers = new AppConfig.OidcProviders() {
            @Override public AppConfig.OidcProviderCreds google() { return "google".equals(enabledProvider) ? enabled : disabled; }
            @Override public AppConfig.OidcProviderCreds microsoft() { return "microsoft".equals(enabledProvider) ? enabled : disabled; }
            @Override public AppConfig.OidcProviderCreds github() { return "github".equals(enabledProvider) ? enabled : disabled; }
        };
        return new AppConfig() {
            @Override public ProblemsConfig problems() { return null; }
            @Override public ConsentTokenConfig consentToken() { return null; }
            @Override public SessionConfig session() { return null; }
            @Override public OAuthConfig oauth() { return null; }
            @Override public OidcProviders oidc() { return providers; }
            @Override public MatchingConfig matching() { return null; }
            @Override public SearchConfig search() { return null; }
            @Override public RetentionConfig retention() { return null; }
            @Override public CvImportConfig cvImport() { return null; }
            @Override public NotificationConfig notification() { return null; }
            @Override public ClusteringConfig clustering() { return null; }
            @Override public MaintenanceConfig maintenance() { return null; }
            @Override public EntitlementsConfig entitlements() { return null; }
            @Override public SkillsConfig skills() { return null; }
            @Override public FrontendConfig frontend() { return null; }
            @Override public OrganizationConfig organization() { return null; }
        };
    }

    private static final class FakeOAuthTokenExchangeService extends OAuthTokenExchangeService {
        private final Map<String, Deque<byte[]>> postResponses = new HashMap<>();
        private final Map<String, Deque<byte[]>> getResponses = new HashMap<>();
        private final Map<String, RuntimeException> postFailures = new HashMap<>();
        private final Map<String, RuntimeException> getFailures = new HashMap<>();
        private final Map<String, Exception> postCheckedFailures = new HashMap<>();

        void enqueuePost(String url, String body) {
            postResponses.computeIfAbsent(url, __ -> new ArrayDeque<>())
                    .add(body.getBytes(StandardCharsets.UTF_8));
        }

        void enqueueGet(String url, String body) {
            getResponses.computeIfAbsent(url, __ -> new ArrayDeque<>())
                    .add(body.getBytes(StandardCharsets.UTF_8));
        }

        void throwOnPostChecked(String url, Exception ex) {
            postCheckedFailures.put(url, ex);
        }

        void throwOnPost(String url, RuntimeException ex) {
            postFailures.put(url, ex);
        }

        void throwOnGet(String url, RuntimeException ex) {
            getFailures.put(url, ex);
        }

        @Override
        byte[] httpPostForm(String url, String formBody) throws Exception {
            if (postCheckedFailures.containsKey(url)) {
                throw postCheckedFailures.get(url);
            }
            if (postFailures.containsKey(url)) {
                throw postFailures.get(url);
            }
            Deque<byte[]> q = postResponses.get(url);
            return q == null || q.isEmpty() ? new byte[0] : q.removeFirst();
        }

        @Override
        byte[] httpPostFormAcceptJson(String url, String formBody) throws Exception {
            if (postCheckedFailures.containsKey(url)) {
                throw postCheckedFailures.get(url);
            }
            if (postFailures.containsKey(url)) {
                throw postFailures.get(url);
            }
            Deque<byte[]> q = postResponses.get(url);
            return q == null || q.isEmpty() ? new byte[0] : q.removeFirst();
        }

        @Override
        byte[] httpGetBearer(String url, String bearer) {
            if (getFailures.containsKey(url)) {
                throw getFailures.get(url);
            }
            Deque<byte[]> q = getResponses.get(url);
            return q == null || q.isEmpty() ? new byte[0] : q.removeFirst();
        }
    }

    private static final class HttpGetBearerProxyService extends OAuthTokenExchangeService {
        OAuthTokenExchangeService.HttpGetResult next;

        @Override
        HttpGetResult httpGetBearerDetailed(String url, String bearer) {
            return next;
        }
    }
}
