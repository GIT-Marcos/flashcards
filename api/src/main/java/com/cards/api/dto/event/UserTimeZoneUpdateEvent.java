package com.cards.api.dto.event;

public record UserTimeZoneUpdateEvent(
    String username,
    String zoneInfo
) {
}
