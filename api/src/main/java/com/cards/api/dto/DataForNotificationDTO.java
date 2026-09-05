package com.cards.api.dto;

public record DataForNotificationDTO(
    Long id,
    String username,
    String email,
    String zoneInfo
) {
}
