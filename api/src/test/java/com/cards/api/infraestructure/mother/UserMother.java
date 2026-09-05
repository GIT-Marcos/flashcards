package com.cards.api.infraestructure.mother;

import com.cards.api.entity.User;

import static com.cards.api.entity.User.UserRole.ROLE_USER;

/**
 * Object Mother para crear instancias de User en tests.
 * Centraliza la lógica de creación con valores por defecto válidos.
 */
public final class UserMother {

    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$mockedHashForTestingOnly1234567890";
    private static final String DEFAULT_ZONE_INFO = "Europe/Madrid";
    private static final int DEFAULT_START_OF_DAY = 6;
    private static final int DEFAULT_SESSION_THRESHOLD = 30;

    private UserMother() {
    }

    /**
     * Crea un usuario mínimo válido para tests básicos.
     */
    public static User createMinimal(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash(DEFAULT_PASSWORD_HASH)
                .zoneInfo(DEFAULT_ZONE_INFO)
                .addRole(ROLE_USER)
                .build();
    }

    /**
     * Crea un usuario con configuración personalizada de zona horaria y startOfDay.
     * Crucial para tests que involucran la lógica SM-2 de Card.applyReview().
     */
    public static User createWithTimeConfig(Long id, String username, String email,
                                            String zoneInfo, int startOfDay) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(DEFAULT_PASSWORD_HASH)
                .zoneInfo(zoneInfo)
                .addRole(ROLE_USER)
                .build();
        user.setId(id);
        user.setStartOfDay(startOfDay);
        user.setSessionThreshold(DEFAULT_SESSION_THRESHOLD);
        return user;
    }

    /**
     * Crea un usuario admin para tests de autorización.
     */
    public static User createAdmin(Long id, String username, String email) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(DEFAULT_PASSWORD_HASH)
                .zoneInfo(DEFAULT_ZONE_INFO)
                .addRole(User.UserRole.ROLE_ADMIN)
                .build();
        user.setId(id);
        return user;
    }

    /**
     * Crea un usuario con lastLogin y lastNotificationSent para tests de actividad.
     */
    public static User createWithActivity(String username, String email,
                                          java.time.Instant lastLogin,
                                          java.time.Instant lastNotificationSent) {
        User user = createMinimal(username, email);
        user.setLastLogin(lastLogin);
        user.setLastNotificationSent(lastNotificationSent);
        return user;
    }

    /**
     * Factory para emails únicos (evita violación de uk_users_email en tests de integración).
     */
    public static String uniqueEmail(String prefix, long suffix) {
        return String.format("%s+%d@example.com", prefix, suffix);
    }

    /**
     * Factory para usernames únicos (evita violación de uk_users_username).
     */
    public static String uniqueUsername(String prefix, long suffix) {
        return String.format("%s_%d", prefix, suffix);
    }
}
