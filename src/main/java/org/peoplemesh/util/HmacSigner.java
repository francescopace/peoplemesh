package org.peoplemesh.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Shared HMAC-SHA256 signer used for session cookies, consent tokens, and OAuth state.
 */
public final class HmacSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private HmacSigner() {}

    public static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(key);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }

    public static boolean verify(String data, String signature, String secret) {
        String expected = sign(data, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
