package com.frontrow.identity;

import com.frontrow.config.ApiException;
import com.frontrow.config.FrontrowProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenSigner {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final FrontrowProperties properties;

    public TokenSigner(FrontrowProperties properties) {
        this.properties = properties;
    }

    public String issue(String userId, Instant expiresAt) {
        String unsigned = encode(userId) + "." + encode(Long.toString(expiresAt.getEpochSecond()));
        return unsigned + "." + encode(sign(unsigned));
    }

    public String verify(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw ApiException.unauthorized();
        }
        String unsigned = parts[0] + "." + parts[1];
        byte[] expected = sign(unsigned);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw ApiException.unauthorized();
        }
        long expiry;
        try {
            expiry = Long.parseLong(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized();
        }
        if (Instant.ofEpochSecond(expiry).isBefore(Instant.now())) {
            throw ApiException.unauthorized();
        }
        try {
            return new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized();
        }
    }

    private String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private byte[] sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getIdentity().getTokenSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign token", exception);
        }
    }
}
