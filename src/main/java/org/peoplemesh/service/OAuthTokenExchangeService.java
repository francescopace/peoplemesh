package org.peoplemesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles OAuth2 authorization-code exchange for all supported providers.
 */
@ApplicationScoped
public class OAuthTokenExchangeService {

    private static final Logger LOG = Logger.getLogger(OAuthTokenExchangeService.class);

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AppConfig appConfig;

    public OidcSubject exchangeAndResolveSubject(String provider, String code, String redirectUri) {
        try {
            return switch (provider) {
                case "google" -> exchangeGoogle(code, redirectUri);
                case "microsoft" -> exchangeMicrosoft(code, redirectUri);
                case "github" -> exchangeGitHub(code, redirectUri);
                default -> null;
            };
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Token exchange failed for provider: " + provider, e);
        }
    }

    public URI buildAuthorizeUri(String provider, String redirectUri, String state) {
        AppConfig.OidcProviderCreds c = creds(provider);
        if (c == null || !isConfigured(c)) return null;
        return switch (provider) {
            case "google" -> buildUri("https://accounts.google.com/o/oauth2/v2/auth",
                    c.clientId(), redirectUri, state,
                    "openid email profile", "&prompt=select_account");
            case "microsoft" -> buildUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    c.clientId(), redirectUri, state,
                    "openid email profile", "&response_mode=query");
            case "github" -> buildUri("https://github.com/login/oauth/authorize",
                    c.clientId(), redirectUri, state, "user:email", "");
            default -> null;
        };
    }

    public boolean isProviderEnabled(String provider) {
        AppConfig.OidcProviderCreds c = creds(provider);
        return c != null && isConfigured(c);
    }

    private static final List<String> LOGIN_PROVIDERS = List.of("google", "microsoft");

    public boolean isLoginEnabled(String provider) {
        return LOGIN_PROVIDERS.contains(provider) && isProviderEnabled(provider);
    }

    private AppConfig.OidcProviderCreds creds(String provider) {
        return switch (provider) {
            case "google" -> appConfig.oidc().google();
            case "microsoft" -> appConfig.oidc().microsoft();
            case "github" -> appConfig.oidc().github();
            default -> null;
        };
    }

    private URI buildUri(String baseUrl, String clientId, String redirectUri,
                         String state, String scope, String extra) {
        String q = "client_id=" + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(scope)
                + "&state=" + enc(state)
                + extra;
        return URI.create(baseUrl + "?" + q);
    }

    private OidcSubject exchangeMicrosoft(String code, String redirectUri) throws Exception {
        AppConfig.OidcProviderCreds c = creds("microsoft");
        if (c == null || !isConfigured(c)) return null;
        String body = tokenBody(code, c.clientId(), c.clientSecret(), redirectUri, true);
        byte[] resp = httpPostForm("https://login.microsoftonline.com/common/oauth2/v2.0/token", body);
        JsonNode json = objectMapper.readTree(resp);
        String access = text(json, "access_token");
        if (access == null) return null;
        byte[] userinfo = httpGetBearer("https://graph.microsoft.com/oidc/userinfo", access);
        JsonNode u = objectMapper.readTree(userinfo);
        String sub = text(u, "sub");
        if (sub == null) return null;
        return new OidcSubject(sub,
                text(u, "name"),
                text(u, "given_name"),
                text(u, "family_name"),
                text(u, "email"),
                null, null, null,
                text(u, "picture"), null);
    }

    private OidcSubject exchangeGoogle(String code, String redirectUri) throws Exception {
        AppConfig.OidcProviderCreds c = creds("google");
        if (c == null || !isConfigured(c)) return null;
        String body = tokenBody(code, c.clientId(), c.clientSecret(), redirectUri, true);
        byte[] resp = httpPostForm("https://oauth2.googleapis.com/token", body);
        JsonNode json = objectMapper.readTree(resp);
        String access = text(json, "access_token");
        if (access == null) return null;
        byte[] userinfo = httpGetBearer("https://www.googleapis.com/oauth2/v3/userinfo", access);
        JsonNode u = objectMapper.readTree(userinfo);
        String sub = text(u, "sub");
        if (sub == null) return null;
        return new OidcSubject(sub,
                text(u, "name"),
                text(u, "given_name"),
                text(u, "family_name"),
                text(u, "email"),
                null, null,
                text(u, "locale"),
                text(u, "picture"),
                text(u, "hd"));
    }

    private OidcSubject exchangeGitHub(String code, String redirectUri) throws Exception {
        AppConfig.OidcProviderCreds c = creds("github");
        if (c == null || !isConfigured(c)) return null;
        String body = tokenBody(code, c.clientId(), c.clientSecret(), redirectUri, false);
        byte[] resp = httpPostFormAcceptJson("https://github.com/login/oauth/access_token", body);
        JsonNode json = objectMapper.readTree(resp);
        String access = text(json, "access_token");
        if (access == null) return null;
        byte[] raw = httpGetBearer("https://api.github.com/user", access);
        JsonNode u = objectMapper.readTree(raw);
        JsonNode id = u.get("id");
        if (id == null || id.isNull()) return null;
        String email = text(u, "email");
        if (email == null) {
            email = fetchGitHubPrimaryEmail(access);
        }
        return new OidcSubject(String.valueOf(id.asLong()),
                text(u, "name"),
                null, null,
                email,
                text(u, "bio"),
                null, null,
                text(u, "avatar_url"), null);
    }

    public GitHubEnrichedResult exchangeGitHubEnriched(String code, String redirectUri) {
        try {
            return doExchangeGitHubEnriched(code, redirectUri);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Token exchange failed for provider: github", e);
        }
    }

    private GitHubEnrichedResult doExchangeGitHubEnriched(String code, String redirectUri) throws Exception {
        AppConfig.OidcProviderCreds c = creds("github");
        if (c == null || !isConfigured(c)) return null;
        String body = tokenBody(code, c.clientId(), c.clientSecret(), redirectUri, false);
        byte[] resp = httpPostFormAcceptJson("https://github.com/login/oauth/access_token", body);
        JsonNode json = objectMapper.readTree(resp);
        String access = text(json, "access_token");
        if (access == null) return null;

        byte[] raw = httpGetBearer("https://api.github.com/user", access);
        JsonNode u = objectMapper.readTree(raw);
        JsonNode id = u.get("id");
        if (id == null || id.isNull()) return null;
        String email = text(u, "email");
        if (email == null) {
            email = fetchGitHubPrimaryEmail(access);
        }
        String login = text(u, "login");
        OidcSubject subject = new OidcSubject(String.valueOf(id.asLong()),
                text(u, "name"), null, null, email,
                text(u, "bio"), null, null,
                text(u, "avatar_url"), text(u, "company"));

        Map<String, Long> langBytes = new LinkedHashMap<>();
        Set<String> repoTopics = new LinkedHashSet<>();
        try {
            byte[] reposRaw = httpGetBearer(
                    "https://api.github.com/user/repos?sort=pushed&per_page=20&type=owner", access);
            JsonNode repos = objectMapper.readTree(reposRaw);
            if (repos != null && repos.isArray()) {
                for (JsonNode repo : repos) {
                    if (repo.has("fork") && repo.get("fork").asBoolean()) continue;
                    String fullName = text(repo, "full_name");
                    if (fullName == null && login != null) {
                        fullName = login + "/" + text(repo, "name");
                    }
                    if (fullName != null) {
                        try {
                            byte[] langRaw = httpGetBearer(
                                    "https://api.github.com/repos/" + fullName + "/languages", access);
                            JsonNode langNode = objectMapper.readTree(langRaw);
                            if (langNode != null && langNode.isObject()) {
                                for (var e : langNode.properties()) {
                                    langBytes.merge(e.getKey(), e.getValue().asLong(), (a, b) -> a + b);
                                }
                            }
                        } catch (Exception e) {
                            LOG.warnf("Failed to parse GitHub languages for repo: %s", e.getMessage());
                        }
                    }
                    JsonNode topicsNode = repo.get("topics");
                    if (topicsNode != null && topicsNode.isArray()) {
                        for (JsonNode t : topicsNode) {
                            if (t.isTextual() && !t.asText().isBlank()) repoTopics.add(t.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("GitHub repo enrichment failed: %s", e.getMessage());
        }

        List<String> sortedLangs = langBytes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        return new GitHubEnrichedResult(subject, sortedLangs, new ArrayList<>(repoTopics));
    }

    private String fetchGitHubPrimaryEmail(String accessToken) {
        try {
            byte[] raw = httpGetBearer("https://api.github.com/user/emails", accessToken);
            JsonNode emails = objectMapper.readTree(raw);
            if (emails == null || !emails.isArray()) return null;
            for (JsonNode e : emails) {
                if (e.has("primary") && e.get("primary").asBoolean()
                        && e.has("verified") && e.get("verified").asBoolean()) {
                    return text(e, "email");
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to parse GitHub primary email: %s", e.getMessage());
        }
        return null;
    }

    private String tokenBody(String code, String clientId, String clientSecret,
                             String redirectUri, boolean includeGrantType) {
        String body = "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri);
        if (includeGrantType) {
            body += "&grant_type=authorization_code";
        }
        return body;
    }

    // === HTTP helpers ===

    byte[] httpPostForm(String url, String formBody) throws Exception {
        java.net.http.HttpClient client = this.httpClient;
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        java.net.http.HttpResponse<byte[]> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return res.statusCode() / 100 != 2 ? new byte[0] : res.body();
    }

    byte[] httpPostFormAcceptJson(String url, String formBody) throws Exception {
        java.net.http.HttpClient client = this.httpClient;
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        java.net.http.HttpResponse<byte[]> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return res.statusCode() / 100 != 2 ? new byte[0] : res.body();
    }

    byte[] httpGetBearer(String url, String bearer) throws Exception {
        HttpGetResult result = httpGetBearerDetailed(url, bearer);
        return result.isSuccess() ? result.body() : new byte[0];
    }

    HttpGetResult httpGetBearerDetailed(String url, String bearer) throws Exception {
        java.net.http.HttpClient client = this.httpClient;
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .GET()
                .build();
        java.net.http.HttpResponse<byte[]> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return new HttpGetResult(res.statusCode(), res.body());
    }

    record HttpGetResult(int statusCode, byte[] body) {
        boolean isSuccess() { return statusCode / 100 == 2; }
        String bodyAsText() {
            if (body == null || body.length == 0) return "<empty>";
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    // === JSON/string helpers ===

    static String text(JsonNode n, String field) {
        if (n == null || field == null || field.isBlank()) return null;
        JsonNode v = n.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static boolean isConfigured(AppConfig.OidcProviderCreds creds) {
        return isRealValue(creds.clientId()) && isRealValue(creds.clientSecret());
    }

    private static boolean isRealValue(String value) {
        return value != null && !value.isBlank() && !"none".equals(value);
    }
}
