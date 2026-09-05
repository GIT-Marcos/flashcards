package com.cards.api.util;

import com.cards.api.exception.domain.InvalidTimeZoneException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimeZoneUtils")
class TimeZoneUtilsTest {

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should return true for valid IANA zone")
        void validIanaZone() {
            assertThat(TimeZoneUtils.isValid("America/New_York")).isTrue();
        }

        @Test
        @DisplayName("should return true for valid UTC offset")
        void validUtcOffset() {
            assertThat(TimeZoneUtils.isValid("+01:00")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid zone")
        void invalidZone() {
            assertThat(TimeZoneUtils.isValid("Invalid/Zone")).isFalse();
        }

        @Test
        @DisplayName("should return false for blank zone")
        void blankZone() {
            assertThat(TimeZoneUtils.isValid("")).isFalse();
        }

        @Test
        @DisplayName("should return false for null zone")
        void nullZone() {
            assertThat(TimeZoneUtils.isValid(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseOrThrow")
    class ParseOrThrow {

        @Test
        @DisplayName("should return ZoneId for valid zone")
        void validZone() {
            assertThat(TimeZoneUtils.parseOrThrow("America/New_York"))
                    .isEqualTo(ZoneId.of("America/New_York"));
        }

        @Test
        @DisplayName("should throw InvalidTimeZoneException for invalid zone")
        void invalidZone() {
            assertThatThrownBy(() -> TimeZoneUtils.parseOrThrow("Bad/Zone"))
                    .isInstanceOf(InvalidTimeZoneException.class);
        }

        @Test
        @DisplayName("should throw InvalidTimeZoneException for null")
        void nullZone() {
            assertThatThrownBy(() -> TimeZoneUtils.parseOrThrow(null))
                    .isInstanceOf(InvalidTimeZoneException.class);
        }

        @Test
        @DisplayName("should throw InvalidTimeZoneException for blank")
        void blankZone() {
            assertThatThrownBy(() -> TimeZoneUtils.parseOrThrow(""))
                    .isInstanceOf(InvalidTimeZoneException.class);
        }
    }

    @Nested
    @DisplayName("parseOrFallback")
    class ParseOrFallback {

        private static final ZoneId FALLBACK = ZoneId.of("UTC");

        @Test
        @DisplayName("should return ZoneId for valid zone")
        void validZone() {
            assertThat(TimeZoneUtils.parseOrFallback("Europe/Madrid", FALLBACK))
                    .isEqualTo(ZoneId.of("Europe/Madrid"));
        }

        @Test
        @DisplayName("should return fallback for invalid zone")
        void invalidZone() {
            assertThat(TimeZoneUtils.parseOrFallback("Bad/Zone", FALLBACK))
                    .isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("should return fallback for null")
        void nullZone() {
            assertThat(TimeZoneUtils.parseOrFallback(null, FALLBACK))
                    .isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("should return fallback for blank")
        void blankZone() {
            assertThat(TimeZoneUtils.parseOrFallback("", FALLBACK))
                    .isEqualTo(FALLBACK);
        }
    }
}
