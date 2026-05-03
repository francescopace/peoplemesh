package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.HmacSigner;
import org.peoplemesh.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SessionService {

    public static final String COOKIE_NAME = "pm_session";

    /** Browser session lifetime (7 days). */
    private static final long SESSION_TTL_SECONDS = 60L * 60L * 24L * 7L;

    @Inject
    AppConfig config;

    public record PmSession(UUID userId, String provider, String displayName) {}

    /**
     * Signed cookie value: base64url(userId|provider|expEpoch|displayNameB64?) + "." + hexHmac
     */
    public String encodeSession(UUID userId, String provider) {
        return encodeSession(userId, provider, null);
    }

    public String encodeSession(UUID userId, String provider, String displayName) {
        long exp = Instant.now().getEpochSecond() + SESSION_TTL_SECONDS;
        String raw = userId + "|" + provider + "|" + exp;
        String normalizedName = normalizeDisplayName(displayName);
        if (normalizedName != null) {
            String encodedName = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(normalizedName.getBytes(StandardCharsets.UTF_8));
            raw = raw + "|" + encodedName;
        }
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + hmacSign(encodedPayload);
    }

    public Optional<PmSession> decodeSession(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String encodedPayload = parts[0];
        String signature = parts[1];

        if (!HmacSigner.verify(encodedPayload, signature, config.session().secret())) {
            return Optional.empty();
        }

        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", 4);
            if (fields.length < 3 || fields.length > 4) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(fields[0]);
            String prov = fields[1];
            long exp = Long.parseLong(fields[2]);
            if (Instant.now().getEpochSecond() > exp) {
                return Optional.empty();
            }
            String displayName = null;
            if (fields.length == 4 && fields[3] != null && !fields[3].isBlank()) {
                displayName = decodeDisplayName(fields[3]).orElse(null);
            }
            @SuppressWarnings("null")
            Optional<PmSession> session = Optional.of(new PmSession(userId, prov, displayName));
            return session;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public int sessionMaxAgeSeconds() {
        return (int) Math.min(SESSION_TTL_SECONDS, Integer.MAX_VALUE);
    }

    // === OAuth state HMAC ===

    private static final long STATE_TTL_SECONDS = 600;

    public record OAuthStatePayload(String intent, String context) {}

    public String signOAuthState(String provider, String intent) {
        return signOAuthState(provider, intent, null);
    }

    public String signOAuthState(String provider, String intent, String context) {
        long exp = Instant.now().getEpochSecond() + STATE_TTL_SECONDS;
        String nonce = UUID.randomUUID().toString();
        String normalizedContext = normalizeDisplayName(context);
        String raw = provider + "|" + intent + "|" + nonce + "|" + exp;
        if (normalizedContext != null) {
            String encodedContext = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(normalizedContext.getBytes(StandardCharsets.UTF_8));
            raw = raw + "|" + encodedContext;
        }
        String enc = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return enc + "." + oauthHmacSign(enc);
    }

    public OAuthStatePayload verifyOAuthState(String token, String expectedProvider) {
        if (token == null || !token.contains(".")) return null;
        String[] parts = token.split("\\.", 2);
        String enc = parts[0];
        String sig = parts[1];
        if (!HmacSigner.verify(enc, sig, config.oauth().stateSecret())) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(enc), StandardCharsets.UTF_8);
            String[] f = raw.split("\\|", 5);
            if (f.length < 4 || f.length > 5) return null;
            if (!expectedProvider.equals(f[0])) return null;
            long exp = Long.parseLong(f[3]);
            if (Instant.now().getEpochSecond() > exp) return null;
            String context = null;
            if (f.length == 5 && f[4] != null && !f[4].isBlank()) {
                context = decodeDisplayName(f[4]).orElse(null);
            }
            return new OAuthStatePayload(f[1], context);
        } catch (Exception e) {
            return null;
        }
    }

    private String oauthHmacSign(String data) {
        return HmacSigner.sign(data, config.oauth().stateSecret());
    }

    private String hmacSign(String data) {
        return HmacSigner.sign(data, config.session().secret());
    }

    private static String normalizeDisplayName(String displayName) {
        return StringUtils.normalizeText(displayName);
    }

    private static Optional<String> decodeDisplayName(String encoded) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String normalized = normalizeDisplayName(value);
            return Optional.ofNullable(normalized);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
