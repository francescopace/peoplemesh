package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpOAuthService {

    private static final Logger LOG = Logger.getLogger(McpOAuthService.class);
    private static final long AUTH_REQUEST_TTL_SECONDS = 300;
    private static final long AUTH_CODE_TTL_SECONDS = 300;
    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600;

    private final Map<String, RegisteredClient> clients = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationRequest> authorizationRequests = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCode> authorizationCodes = new ConcurrentHashMap<>();
    private final Map<String, AccessToken> accessTokens = new ConcurrentHashMap<>();

    public record RegisteredClient(
            String clientId,
            String clientSecret,
            String clientName,
            List<String> redirectUris,
            List<String> grantTypes,
            String tokenEndpointAuthMethod
    ) {}

    public record AuthorizationRequest(
            String requestId,
            String clientId,
            String redirectUri,
            String state,
            String codeChallenge,
            String scope,
            long expiresAtEpochSeconds
    ) {}

    public record AccessTokenPrincipal(
            UUID userId,
            String provider,
            String displayName
    ) {}

    private record AuthorizationCode(
            String code,
            String clientId,
            String redirectUri,
            String codeChallenge,
            UUID userId,
            String provider,
            String displayName,
            long expiresAtEpochSeconds
    ) {}

    private record AccessToken(
            String token,
            UUID userId,
            String provider,
            String displayName,
            String clientId,
            long expiresAtEpochSeconds
    ) {}

    public RegisteredClient registerClient(
            String clientName,
            List<String> redirectUris,
            List<String> grantTypes,
            String tokenEndpointAuthMethod
    ) {
        String authMethod = normalizeAuthMethod(tokenEndpointAuthMethod);
        String clientId = "pm-" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = "none".equals(authMethod) ? null : UUID.randomUUID().toString().replace("-", "");
        RegisteredClient client = new RegisteredClient(
                clientId,
                clientSecret,
                normalizeClientName(clientName),
                normalizeStringList(redirectUris),
                normalizeGrantTypes(grantTypes),
                authMethod
        );
        clients.put(clientId, client);
        return client;
    }

    public Optional<AuthorizationRequest> createAuthorizationRequest(
            String clientId,
            String redirectUri,
            String state,
            String codeChallenge,
            String scope
    ) {
        Optional<RegisteredClient> clientOpt = getClient(clientId);
        if (clientOpt.isEmpty()) {
            return Optional.empty();
        }
        RegisteredClient client = clientOpt.get();
        if (!client.redirectUris().isEmpty() && !client.redirectUris().contains(redirectUri)) {
            return Optional.empty();
        }
        String requestId = UUID.randomUUID().toString();
        AuthorizationRequest request = new AuthorizationRequest(
                requestId,
                clientId,
                redirectUri,
                state,
                codeChallenge,
                scope,
                Instant.now().getEpochSecond() + AUTH_REQUEST_TTL_SECONDS
        );
        authorizationRequests.put(requestId, request);
        return Optional.of(request);
    }

    public Optional<AuthorizationRequest> getAuthorizationRequest(String requestId) {
        AuthorizationRequest request = authorizationRequests.get(requestId);
        if (request == null || request.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
            authorizationRequests.remove(requestId);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public Optional<String> completeAuthorization(
            String requestId,
            UUID userId,
            String provider,
            String displayName
    ) {
        AuthorizationRequest request = authorizationRequests.remove(requestId);
        if (request == null || request.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        AuthorizationCode authorizationCode = new AuthorizationCode(
                code,
                request.clientId(),
                request.redirectUri(),
                request.codeChallenge(),
                userId,
                provider,
                displayName,
                Instant.now().getEpochSecond() + AUTH_CODE_TTL_SECONDS
        );
        authorizationCodes.put(code, authorizationCode);
        String redirectUri = buildAuthorizationRedirect(request.redirectUri(), code, request.state());
        return Optional.of(redirectUri);
    }

    public Optional<Map<String, Object>> exchangeAuthorizationCode(
            String clientId,
            String clientSecret,
            String code,
            String redirectUri,
            String codeVerifier
    ) {
        Optional<RegisteredClient> clientOpt = validateClient(clientId, clientSecret);
        if (clientOpt.isEmpty()) {
            return Optional.empty();
        }
        AuthorizationCode authorizationCode = authorizationCodes.remove(code);
        if (authorizationCode == null || authorizationCode.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        if (!authorizationCode.clientId().equals(clientId) || !authorizationCode.redirectUri().equals(redirectUri)) {
            return Optional.empty();
        }
        if (authorizationCode.codeChallenge() != null && !authorizationCode.codeChallenge().isBlank()) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                return Optional.empty();
            }
            if (!authorizationCode.codeChallenge().equals(computeS256Challenge(codeVerifier))) {
                return Optional.empty();
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        AccessToken accessToken = new AccessToken(
                token,
                authorizationCode.userId(),
                authorizationCode.provider(),
                authorizationCode.displayName(),
                clientId,
                Instant.now().getEpochSecond() + ACCESS_TOKEN_TTL_SECONDS
        );
        accessTokens.put(token, accessToken);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token);
        body.put("token_type", "Bearer");
        body.put("expires_in", ACCESS_TOKEN_TTL_SECONDS);
        return Optional.of(body);
    }

    public Optional<AccessTokenPrincipal> resolveAccessToken(String token) {
        AccessToken accessToken = accessTokens.get(token);
        if (accessToken == null) {
            return Optional.empty();
        }
        if (accessToken.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
            accessTokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(new AccessTokenPrincipal(
                accessToken.userId(),
                accessToken.provider(),
                accessToken.displayName()
        ));
    }

    public Optional<RegisteredClient> getClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(clients.get(clientId));
    }

    private Optional<RegisteredClient> validateClient(String clientId, String clientSecret) {
        Optional<RegisteredClient> clientOpt = getClient(clientId);
        if (clientOpt.isEmpty()) {
            return Optional.empty();
        }
        RegisteredClient client = clientOpt.get();
        if ("none".equals(client.tokenEndpointAuthMethod())) {
            return Optional.of(client);
        }
        if (client.clientSecret() == null || !client.clientSecret().equals(clientSecret)) {
            return Optional.empty();
        }
        return Optional.of(client);
    }

    private static String buildAuthorizationRedirect(String redirectUri, String code, String state) {
        StringBuilder location = new StringBuilder(redirectUri);
        location.append(redirectUri.contains("?") ? "&" : "?");
        location.append("code=").append(urlEncode(code));
        if (state != null && !state.isBlank()) {
            location.append("&state=").append(urlEncode(state));
        }
        return location.toString();
    }

    private static String normalizeAuthMethod(String authMethod) {
        if (authMethod == null || authMethod.isBlank()) {
            return "none";
        }
        String normalized = authMethod.trim();
        if (!"none".equals(normalized) && !"client_secret_post".equals(normalized)) {
            LOG.warnf("Unsupported token_endpoint_auth_method=%s, defaulting to none", normalized);
            return "none";
        }
        return normalized;
    }

    private static List<String> normalizeGrantTypes(List<String> grantTypes) {
        if (grantTypes == null || grantTypes.isEmpty()) {
            return List.of("authorization_code");
        }
        List<String> normalized = new ArrayList<>();
        for (String grantType : grantTypes) {
            if (grantType == null || grantType.isBlank()) {
                continue;
            }
            normalized.add(grantType.trim());
        }
        if (normalized.isEmpty()) {
            return List.of("authorization_code");
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static String normalizeClientName(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return "MCP Client";
        }
        return clientName.trim();
    }

    private static String computeS256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
