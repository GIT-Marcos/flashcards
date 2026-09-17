package com.cards.api.service;

import com.cards.api.config.properties.ApplicationProperties;
import io.jsonwebtoken.io.Decoders;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class PasswordFingerprintService {

    private final SecretKeySpec keySpec;

    public PasswordFingerprintService(ApplicationProperties properties) {
        String secret = properties.getSecurity().getPwdFingerprintSecret();
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "PWD_FINGERPRINT_SECRET must decode to at least 32 bytes, got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
        try {
            Mac instance = Mac.getInstance("HmacSHA256");
            instance.init(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to initialize HMAC-SHA256", e);
        }
    }

    public String compute(String bcryptHash) {
        if (bcryptHash == null) {
            throw new IllegalArgumentException("bcryptHash must not be null");
        }
        byte[] hmac;
        try {
            Mac instance = Mac.getInstance("HmacSHA256");
            instance.init(keySpec);
            hmac = instance.doFinal(bcryptHash.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
        return Base64.getEncoder().encodeToString(hmac);
    }

    public boolean matches(String bcryptHash, String claimFingerprint) {
        if (bcryptHash == null || claimFingerprint == null) {
            return false;
        }
        String expected = compute(bcryptHash);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                claimFingerprint.getBytes(StandardCharsets.UTF_8));
    }
}
