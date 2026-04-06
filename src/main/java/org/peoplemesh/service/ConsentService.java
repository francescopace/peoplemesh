package org.peoplemesh.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.model.ProfileConsent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Generates and validates HMAC-SHA256 signed consent tokens.
 * Tokens are single-use (tracked in Redis) with a configurable TTL.
 */
@ApplicationScoped
public class ConsentService {

    private static final String REDIS_PREFIX = "consent:";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String CURRENT_POLICY_VERSION = "1.0";

    @Inject
    AppConfig config;

    @Inject
    RedisDataSource redisDataSource;

    /**
     * Generates a consent token: base64(userId|timestamp|scope) + "." + hmac_signature
     */
    public String generateToken(UUID userId, String scope) {
        long now = Instant.now().getEpochSecond();
        String payload = userId.toString() + "|" + now + "|" + scope;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSign(encodedPayload);
        return encodedPayload + "." + signature;
    }

    /**
     * Validates signature, TTL, scope, and single-use via Redis.
     * Returns the userId if valid, throws otherwise.
     *
     * @param requiredScope the scope this token must authorize (e.g. "profile_storage")
     */
    public UUID validateAndConsume(String token, String requiredScope) {
        if (token == null || !token.contains(".")) {
            throw new IllegalArgumentException("Invalid consent token format");
        }

        String[] parts = token.split("\\.", 2);
        String encodedPayload = parts[0];
        String signature = parts[1];

        String expectedSig = hmacSign(encodedPayload);
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Consent token signature verification failed");
        }

        String payload = new String(
                Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|", 3);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Malformed consent token payload");
        }

        UUID userId = UUID.fromString(fields[0]);
        long timestamp = Long.parseLong(fields[1]);
        String tokenScope = fields[2];
        long now = Instant.now().getEpochSecond();

        if (now - timestamp > config.consentToken().ttlSeconds()) {
            throw new SecurityException("Consent token expired");
        }

        if (requiredScope != null && !requiredScope.equals(tokenScope)) {
            throw new SecurityException("Consent token scope mismatch: expected " +
                    requiredScope + " but token has " + tokenScope);
        }

        String redisKey = REDIS_PREFIX + token;
        ValueCommands<String, String> commands = redisDataSource.value(String.class, String.class);
        Boolean wasSet = commands.setnx(redisKey, "used");
        if (!Boolean.TRUE.equals(wasSet)) {
            throw new SecurityException("Consent token already used");
        }
        commands.getex(redisKey, new io.quarkus.redis.datasource.value.GetExArgs()
                .ex(Duration.ofSeconds(config.consentToken().ttlSeconds() * 2L)));

        return userId;
    }

    /**
     * Marks a consumed token as unused so it can be retried
     * (called on rollback when profile submission fails after token consumption).
     */
    public void releaseToken(String token) {
        if (token == null) return;
        String redisKey = REDIS_PREFIX + token;
        ValueCommands<String, String> commands = redisDataSource.value(String.class, String.class);
        commands.getdel(redisKey);
    }

    @Transactional
    public void recordConsent(UUID userId, String scope, String ipHash) {
        ProfileConsent consent = new ProfileConsent();
        consent.userId = userId;
        consent.scope = scope;
        consent.grantedAt = Instant.now();
        consent.ipHash = ipHash;
        consent.policyVersion = CURRENT_POLICY_VERSION;
        consent.persist();
    }

    @Transactional
    public void revokeAllConsents(UUID userId) {
        ProfileConsent.revokeAllForUser(userId);
    }

    private String hmacSign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec key = new SecretKeySpec(
                    config.consentToken().secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(key);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
