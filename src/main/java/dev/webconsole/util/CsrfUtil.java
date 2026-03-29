package dev.webconsole.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 based CSRF token generator/validator.
 * Token format: nonce.HMAC(secret, nonce)
 */
public class CsrfUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] secret;

    public CsrfUtil(String secretString) {
        this.secret = secretString.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken() {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String nonceB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String hmac = hmacSha256(nonceB64);
        return nonceB64 + "." + hmac;
    }

    public boolean validateToken(String token) {
        if (token == null || !token.contains(".")) return false;
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) return false;
        String expected = hmacSha256(parts[0]);
        return constantTimeEquals(parts[1], expected);
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
