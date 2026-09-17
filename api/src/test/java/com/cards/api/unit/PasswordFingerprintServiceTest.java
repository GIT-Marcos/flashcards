package com.cards.api.unit;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.service.PasswordFingerprintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordFingerprintService")
class PasswordFingerprintServiceTest {

    private static final String SECRET_A = Base64.getEncoder()
            .encodeToString("a".repeat(40).getBytes(StandardCharsets.UTF_8));
    private static final String SECRET_B = Base64.getEncoder()
            .encodeToString("b".repeat(40).getBytes(StandardCharsets.UTF_8));
    private static final String SHORT_SECRET = Base64.getEncoder()
            .encodeToString("short".getBytes(StandardCharsets.UTF_8));
    private static final String BCRYPT_1 = "$2a$12$firstBcryptHashWithSaltValue";
    private static final String BCRYPT_2 = "$2a$12$secondBcryptHashWithSaltValu";

    private ApplicationProperties propertiesWith(String secret) {
        var properties = new ApplicationProperties();
        properties.getSecurity().setPwdFingerprintSecret(secret);
        return properties;
    }

    // ======================== CONSTRUCTOR ========================

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("should throw IllegalStateException when secret decodes to less than 32 bytes")
        void shouldThrowWhenSecretDecodesToLessThan32Bytes() {
            assertThatThrownBy(() -> new PasswordFingerprintService(propertiesWith(SHORT_SECRET)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 32 bytes");
        }
    }

    // ======================== COMPUTE ========================

    @Nested
    @DisplayName("compute")
    class Compute {

        @Test
        @DisplayName("should be deterministic for the same hash")
        void shouldBeDeterministicForSameHash() {
            var service = new PasswordFingerprintService(propertiesWith(SECRET_A));

            String first = service.compute(BCRYPT_1);
            String second = service.compute(BCRYPT_1);

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("should differ for different bcrypt hashes")
        void shouldDifferForDifferentBcryptHashes() {
            var service = new PasswordFingerprintService(propertiesWith(SECRET_A));

            String first = service.compute(BCRYPT_1);
            String second = service.compute(BCRYPT_2);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("should differ for different secrets")
        void shouldDifferForDifferentSecrets() {
            var serviceA = new PasswordFingerprintService(propertiesWith(SECRET_A));
            var serviceB = new PasswordFingerprintService(propertiesWith(SECRET_B));

            String first = serviceA.compute(BCRYPT_1);
            String second = serviceB.compute(BCRYPT_1);

            assertThat(first).isNotEqualTo(second);
        }
    }

    // ======================== MATCHES ========================

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("should return true for the correct fingerprint")
        void shouldReturnTrueForCorrectFingerprint() {
            var service = new PasswordFingerprintService(propertiesWith(SECRET_A));
            String fingerprint = service.compute(BCRYPT_1);

            assertThat(service.matches(BCRYPT_1, fingerprint)).isTrue();
        }

        @Test
        @DisplayName("should return false for an incorrect fingerprint")
        void shouldReturnFalseForIncorrectFingerprint() {
            var service = new PasswordFingerprintService(propertiesWith(SECRET_A));

            assertThat(service.matches(BCRYPT_1, "not-the-fingerprint")).isFalse();
        }

        @Test
        @DisplayName("should return false when claim is null")
        void shouldReturnFalseWhenClaimIsNull() {
            var service = new PasswordFingerprintService(propertiesWith(SECRET_A));

            assertThat(service.matches(BCRYPT_1, null)).isFalse();
        }
    }
}
