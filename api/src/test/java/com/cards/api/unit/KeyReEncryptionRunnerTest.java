package com.cards.api.unit;

import com.cards.api.config.KeyReEncryptionRunner;
import com.cards.api.config.properties.EncryptionProperties;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import com.cards.api.repo.UserApiKeyRepository;
import com.cards.api.util.AiProvider;
import com.cards.api.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyReEncryptionRunner")
class KeyReEncryptionRunnerTest {

    private static final byte[] V1_KEY_BYTES = new byte[32];
    private static final String V1_SECRET = Base64.getEncoder().encodeToString(V1_KEY_BYTES);

    private static final byte[] V2_KEY_BYTES = new byte[32];

    static {
        V2_KEY_BYTES[0] = 1;
    }

    private static final String V2_SECRET = Base64.getEncoder().encodeToString(V2_KEY_BYTES);
    private static final String PLAINTEXT = "sk-proj-abc12345";
    private static final Long KEY_ID_1 = 100L;
    private static final Long KEY_ID_2 = 200L;
    private static final AiProvider PROVIDER = AiProvider.OPENAI;

    @Mock
    private UserApiKeyRepository repository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EncryptionProperties encryptionProperties;

    private User owner;
    private UserApiKey key1;
    private UserApiKey key2;

    @BeforeEach
    void setUp() {
        when(encryptionProperties.getSecret()).thenReturn(V1_SECRET);

        owner = User.builder()
            .username("owner")
            .email("owner@email.com")
            .passwordHash("hash")
            .zoneInfo("UTC")
            .addRole(User.UserRole.ROLE_USER)
            .build();
        owner.setId(1L);

        key1 = UserApiKey.builder()
            .user(owner)
            .provider(PROVIDER)
            .keyAlias("...2345")
            .encryptedKey(PLAINTEXT)
            .build();
        key1.setId(KEY_ID_1);

        key2 = UserApiKey.builder()
            .user(owner)
            .provider(AiProvider.ANTHROPIC)
            .keyAlias("...6789")
            .encryptedKey(PLAINTEXT)
            .build();
        key2.setId(KEY_ID_2);
    }

    private KeyReEncryptionRunner createRunner(String v2Secret) {
        return new KeyReEncryptionRunner(repository, jdbcTemplate, encryptionProperties, v2Secret);
    }

    // ======================== EARLY EXITS ========================

    @Nested
    @DisplayName("early exits")
    class EarlyExits {

        @Test
        @DisplayName("should skip when V2 is not set")
        void shouldSkipWhenV2NotSet() throws Exception {
            KeyReEncryptionRunner runner = createRunner("");

            runner.run();

            verifyNoInteractions(repository);
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("should skip when V2 equals V1")
        void shouldSkipWhenV2EqualsV1() throws Exception {
            KeyReEncryptionRunner runner = createRunner(V1_SECRET);

            runner.run();

            verifyNoInteractions(repository);
            verifyNoInteractions(jdbcTemplate);
        }

        @Test
        @DisplayName("should skip when no keys exist")
        void shouldSkipWhenNoKeys() throws Exception {
            when(repository.findAll()).thenReturn(List.of());

            KeyReEncryptionRunner runner = createRunner(V2_SECRET);
            runner.run();

            verify(repository).findAll();
            verifyNoInteractions(jdbcTemplate);
        }
    }

    // ======================== SUCCESS ========================

    @Nested
    @DisplayName("successful re-encryption")
    class SuccessfulReEncryption {

        @Test
        @DisplayName("should re-encrypt all keys and verify them")
        void shouldReEncryptAllKeys() throws Exception {
            String expectedCiphertext1 = EncryptionUtil.encrypt(PLAINTEXT, V2_SECRET);
            String expectedCiphertext2 = EncryptionUtil.encrypt(PLAINTEXT, V2_SECRET);

            when(repository.findAll()).thenReturn(List.of(key1, key2));
            when(jdbcTemplate.queryForObject(
                "SELECT encrypted_key FROM user_api_keys WHERE id = ?",
                String.class, KEY_ID_1
            )).thenReturn(expectedCiphertext1);
            when(jdbcTemplate.queryForObject(
                "SELECT encrypted_key FROM user_api_keys WHERE id = ?",
                String.class, KEY_ID_2
            )).thenReturn(expectedCiphertext2);

            KeyReEncryptionRunner runner = createRunner(V2_SECRET);
            runner.run();

            verify(jdbcTemplate, times(2)).update(
                eq("UPDATE user_api_keys SET encrypted_key = ?, updated_at = NOW() WHERE id = ?"),
                anyString(),
                anyLong()
            );
            verify(jdbcTemplate, times(2)).queryForObject(
                eq("SELECT encrypted_key FROM user_api_keys WHERE id = ?"),
                eq(String.class),
                anyLong()
            );
        }
    }

    // ======================== PARTIAL FAILURE ========================

    @Nested
    @DisplayName("partial failure handling")
    class PartialFailure {

        @Test
        @DisplayName("should log and continue when a key fails to re-encrypt")
        void shouldContinueOnEncryptionFailure() throws Exception {
            when(jdbcTemplate.update(
                eq("UPDATE user_api_keys SET encrypted_key = ?, updated_at = NOW() WHERE id = ?"),
                anyString(),
                eq(KEY_ID_1)
            )).thenThrow(new RuntimeException("DB connection lost"));

            when(repository.findAll()).thenReturn(List.of(key1, key2));

            String expectedCiphertext2 = EncryptionUtil.encrypt(PLAINTEXT, V2_SECRET);
            when(jdbcTemplate.queryForObject(
                "SELECT encrypted_key FROM user_api_keys WHERE id = ?",
                String.class, KEY_ID_2
            )).thenReturn(expectedCiphertext2);

            KeyReEncryptionRunner runner = createRunner(V2_SECRET);
            runner.run();

            verify(jdbcTemplate, times(1)).update(
                eq("UPDATE user_api_keys SET encrypted_key = ?, updated_at = NOW() WHERE id = ?"),
                anyString(),
                eq(KEY_ID_2)
            );
            verify(jdbcTemplate, times(1)).queryForObject(
                eq("SELECT encrypted_key FROM user_api_keys WHERE id = ?"),
                eq(String.class),
                eq(KEY_ID_2)
            );
        }
    }

    // ======================== VERIFICATION FAILURE ========================

    @Nested
    @DisplayName("verification failure")
    class VerificationFailure {

        @Test
        @DisplayName("should abort startup when verification fails")
        void shouldAbortWhenVerificationFails() throws Exception {
            when(repository.findAll()).thenReturn(List.of(key1));
            when(jdbcTemplate.queryForObject(
                "SELECT encrypted_key FROM user_api_keys WHERE id = ?",
                String.class, KEY_ID_1
            )).thenReturn("invalid-base64-data");

            KeyReEncryptionRunner runner = createRunner(V2_SECRET);

            assertThatThrownBy(runner::run)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("verification failed");
        }
    }
}
