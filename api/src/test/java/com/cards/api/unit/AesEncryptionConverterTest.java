package com.cards.api.unit;

import com.cards.api.config.encryption.AesEncryptionConverter;
import com.cards.api.config.properties.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AesEncryptionConverter")
class AesEncryptionConverterTest {

    private static final byte[] KEY_BYTES = new byte[32];
    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(KEY_BYTES);
    private static final String PLAINTEXT = "sk-proj-abc123def456";
    private static final String ANOTHER_PLAINTEXT = "sk-ant-xyz789";

    @Mock
    private EncryptionProperties properties;

    private AesEncryptionConverter converter;

    @BeforeEach
    void setUp() {
        when(properties.getSecret()).thenReturn(BASE64_KEY);
        converter = new AesEncryptionConverter(properties);
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should encrypt plaintext to non-null Base64 string")
        void shouldEncrypt() {
            String encrypted = converter.convertToDatabaseColumn(PLAINTEXT);

            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotBlank();
            assertThat(encrypted).doesNotContain(PLAINTEXT);
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNullWhenNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("should produce different ciphertexts for the same plaintext")
        void shouldEncryptNonDeterministically() {
            String encrypted1 = converter.convertToDatabaseColumn(PLAINTEXT);
            String encrypted2 = converter.convertToDatabaseColumn(PLAINTEXT);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should decrypt previously encrypted value")
        void shouldDecrypt() {
            String encrypted = converter.convertToDatabaseColumn(PLAINTEXT);
            String decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNullWhenNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid Base64")
        void shouldThrowForInvalidBase64() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-valid-base64!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypt");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for tampered ciphertext")
        void shouldThrowForTamperedCiphertext() {
            String encrypted = converter.convertToDatabaseColumn(PLAINTEXT);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            decoded[decoded.length - 1] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decrypt");
        }

        @Test
        @DisplayName("should decrypt multiple different values")
        void shouldDecryptMultipleValues() {
            String enc1 = converter.convertToDatabaseColumn(PLAINTEXT);
            String enc2 = converter.convertToDatabaseColumn(ANOTHER_PLAINTEXT);

            assertThat(converter.convertToEntityAttribute(enc1)).isEqualTo(PLAINTEXT);
            assertThat(converter.convertToEntityAttribute(enc2)).isEqualTo(ANOTHER_PLAINTEXT);
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("should preserve empty string")
        void shouldHandleEmptyString() {
            String encrypted = converter.convertToDatabaseColumn("");
            String decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("should handle long text")
        void shouldHandleLongText() {
            String longText = "A".repeat(5000);
            String encrypted = converter.convertToDatabaseColumn(longText);
            String decrypted = converter.convertToEntityAttribute(encrypted);

            assertThat(decrypted).isEqualTo(longText);
        }
    }
}
