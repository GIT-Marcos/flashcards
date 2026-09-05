package com.cards.api.config;

import com.cards.api.config.properties.EncryptionProperties;
import com.cards.api.entity.UserApiKey;
import com.cards.api.repo.UserApiKeyRepository;
import com.cards.api.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
public class KeyReEncryptionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KeyReEncryptionRunner.class);

    private final UserApiKeyRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final String v1Secret;
    private final String v2Secret;

    public KeyReEncryptionRunner(
        UserApiKeyRepository repository,
        JdbcTemplate jdbcTemplate,
        EncryptionProperties encryptionProperties,
        @Value("${API_KEY_ENCRYPTION_SECRET_V2:}") String v2Secret
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.v1Secret = encryptionProperties.getSecret();
        this.v2Secret = (v2Secret == null ? "" : v2Secret.strip());
    }

    @Override
    public void run(String... args) {
        if (v2Secret.isBlank()) {
            log.info("API_KEY_ENCRYPTION_SECRET_V2 not set — skipping key re-encryption");
            return;
        }
        if (v2Secret.equals(v1Secret)) {
            log.warn("API_KEY_ENCRYPTION_SECRET_V2 equals API_KEY_ENCRYPTION_SECRET — skipping (same key)");
            return;
        }

        log.info("Starting re-encryption of API keys from V1 to V2...");
        List<UserApiKey> keys = repository.findAll();
        if (keys.isEmpty()) {
            log.info("No API keys to re-encrypt");
            return;
        }

        List<Long> migratedIds = new ArrayList<>();
        int count = 0;
        int failed = 0;
        for (UserApiKey key : keys) {
            try {
                String plaintext = key.getEncryptedKey();
                String newCiphertext = EncryptionUtil.encrypt(plaintext, v2Secret);
                // SQL nativo para evitar el converter
                jdbcTemplate.update(
                    "UPDATE user_api_keys SET encrypted_key = ?, updated_at = NOW() WHERE id = ?",
                    newCiphertext, key.getId()
                );
                migratedIds.add(key.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to re-encrypt key ID {}: {}", key.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("--- Key re-encryption complete ---");
        log.info("Re-encrypted {} API keys with V2", count);
        if (failed > 0) {
            log.warn("{} keys failed to re-encrypt — check logs above", failed);
        }

        // Verificar que los registros migrados son legibles con V2
        if (!migratedIds.isEmpty()) {
            log.info("Verifying migrated keys with V2...");
            int verified = 0;
            for (Long id : migratedIds) {
                try {
                    String rawCiphertext = jdbcTemplate.queryForObject(
                        "SELECT encrypted_key FROM user_api_keys WHERE id = ?",
                        String.class, id
                    );
                    EncryptionUtil.decrypt(rawCiphertext, v2Secret);
                    verified++;
                } catch (Exception e) {
                    log.error("Verification FAILED for key ID {} — data may be corrupt!", id, e);
                }
            }
            if (verified < count) {
                throw new RuntimeException(
                    "Key re-encryption verification failed: " + verified + "/" + count
                        + " keys readable with V2. App startup aborted to prevent data corruption."
                );
            }
            log.info("All {} migrated keys verified successfully with V2", verified);
        }

        log.info("NEXT STEP: Update API_KEY_ENCRYPTION_SECRET to the V2 value, "
            + "remove API_KEY_ENCRYPTION_SECRET_V2 env var, and restart.");
    }
}
