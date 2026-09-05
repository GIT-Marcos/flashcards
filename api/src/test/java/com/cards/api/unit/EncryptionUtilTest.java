package com.cards.api.unit;

import com.cards.api.config.encryption.AesEncryptionConverter;
import com.cards.api.config.properties.EncryptionProperties;
import com.cards.api.util.EncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EncryptionUtil")
class EncryptionUtilTest {

    private static final byte[] KEY_BYTES = new byte[32];
    private static final String KEY_A = Base64.getEncoder().encodeToString(KEY_BYTES);
    private static final byte[] DIFFERENT_KEY_BYTES = new byte[32];

    static {
        DIFFERENT_KEY_BYTES[0] = 1;
    }

    private static final String KEY_B = Base64.getEncoder().encodeToString(DIFFERENT_KEY_BYTES);
    private static final String PLAINTEXT = "sk-proj-abc123def456";

    @Nested
    @DisplayName("encrypt")
    class Encrypt {

        @Test
        @DisplayName("should return encrypted non-null Base64 string")
        void shouldEncrypt() {
            String encrypted = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotBlank();
            assertThat(encrypted).doesNotContain(PLAINTEXT);
        }

        @Test
        @DisplayName("should return null for null plaintext")
        void shouldReturnNullForNullInput() {
            assertThat(EncryptionUtil.encrypt(null, KEY_A)).isNull();
        }

        @Test
        @DisplayName("should produce different ciphertexts for the same plaintext")
        void shouldEncryptNonDeterministically() {
            String encrypted1 = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);
            String encrypted2 = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    @Nested
    @DisplayName("decrypt")
    class Decrypt {

        @Test
        @DisplayName("should decrypt value encrypted with the same key")
        void shouldDecryptWithSameKey() {
            String encrypted = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);
            String decrypted = EncryptionUtil.decrypt(encrypted, KEY_A);

            assertThat(decrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("should return null for null ciphertext")
        void shouldReturnNullForNullInput() {
            assertThat(EncryptionUtil.decrypt(null, KEY_A)).isNull();
        }

        @Test
        @DisplayName("should throw when decrypting with a different key")
        void shouldThrowWithWrongKey() {
            String encrypted = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);

            assertThatThrownBy(() -> EncryptionUtil.decrypt(encrypted, KEY_B))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypt");
        }

        @Test
        @DisplayName("should throw for invalid Base64 ciphertext")
        void shouldThrowForInvalidBase64() {
            assertThatThrownBy(() -> EncryptionUtil.decrypt("invalid!!", KEY_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypt");
        }
    }

    @Nested
    @DisplayName("compatibility with AesEncryptionConverter")
    class CompatibilityWithConverter {

        @Test
        @DisplayName("should decrypt what converter encrypted")
        void shouldDecryptConverterOutput() {
            EncryptionProperties props = mock(EncryptionProperties.class);
            when(props.getSecret()).thenReturn(KEY_A);
            AesEncryptionConverter converter = new AesEncryptionConverter(props);

            String converterEncrypted = converter.convertToDatabaseColumn(PLAINTEXT);
            String utilDecrypted = EncryptionUtil.decrypt(converterEncrypted, KEY_A);

            assertThat(utilDecrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("converter should decrypt what util encrypted")
        void converterShouldDecryptUtilOutput() {
            EncryptionProperties props = mock(EncryptionProperties.class);
            when(props.getSecret()).thenReturn(KEY_A);
            AesEncryptionConverter converter = new AesEncryptionConverter(props);

            String utilEncrypted = EncryptionUtil.encrypt(PLAINTEXT, KEY_A);
            String converterDecrypted = converter.convertToEntityAttribute(utilEncrypted);

            assertThat(converterDecrypted).isEqualTo(PLAINTEXT);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            String encrypted = EncryptionUtil.encrypt("", KEY_A);
            String decrypted = EncryptionUtil.decrypt(encrypted, KEY_A);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("should handle long text")
        void shouldHandleLongText() {
            String longText = "A".repeat(5000);
            String encrypted = EncryptionUtil.encrypt(longText, KEY_A);
            String decrypted = EncryptionUtil.decrypt(encrypted, KEY_A);

            assertThat(decrypted).isEqualTo(longText);
        }
    }
}
