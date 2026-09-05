package com.cards.api.util;

public final class StringUtils {

    private StringUtils() {
    }

    public static String sanitizeString(String s) {
        return (s == null) ? null : s.strip().replaceAll("\\s+", " ");
    }
}