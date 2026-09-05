package com.cards.api.util;

import com.cards.api.exception.domain.InvalidTimeZoneException;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class TimeZoneUtils {

    private TimeZoneUtils() {
    }

    public static boolean isValid(String zone) {
        if (zone == null || zone.isBlank()) return false;
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    public static ZoneId parseOrThrow(String zone) {
        if (zone == null || zone.isBlank())
            throw new InvalidTimeZoneException(zone);
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new InvalidTimeZoneException(zone);
        }
    }

    public static ZoneId parseOrFallback(String zone, ZoneId fallback) {
        if (zone == null || zone.isBlank()) return fallback;
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return fallback;
        }
    }
}
