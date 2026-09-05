package com.cards.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
// Flyway es la fuente de verdad del esquema. Los @Index aquí son documentación referencial
// (ddl-auto: validate). Los índices reales en Flyway usan LOWER() para unicidad case-insensitive.
@Table(
        name = "users",
        indexes = {
                @Index(name = "uk_users_username_lower", columnList = "username", unique = true),
                @Index(name = "uk_users_email_lower", columnList = "email", unique = true)
        }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "zone_info", nullable = false)
    private String zoneInfo;

    @Column(name = "last_notification_sent")
    private Instant lastNotificationSent;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    /**
     * Rango de tiempo en minutos que debe pasar entre 2 reviews sucesivas para que se consideren
     * sesiones de estudio diferentes.
     */
    @Column(name = "session_threshold", nullable = false)
    private int sessionThreshold = 30;

    /**
     * Hora del día a la que se normalizan como pendientes las tarjetas.
     */
    @Column(name = "start_of_day", nullable = false)
    private int startOfDay = 6;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    private Set<UserRole> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", orphanRemoval = true)
    private List<Deck> decks = new ArrayList<>();

    protected User() {
    }

    private User(Builder b) {
        this.username = b.username;
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.zoneInfo = b.zoneInfo;
        this.notificationsEnabled = b.notificationsEnabled;
        this.sessionThreshold = b.sessionThreshold;
        this.startOfDay = b.startOfDay;
        if (b.roles != null && !b.roles.isEmpty())
            this.roles.addAll(b.roles);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String username;
        private String email;
        private String passwordHash;
        private String zoneInfo;
        private boolean notificationsEnabled = true;
        private int sessionThreshold = 30;
        private int startOfDay = 6;
        private Set<UserRole> roles = new HashSet<>();

        private Builder() {
        }

        public Builder username(String username) {
            this.username = Objects.requireNonNull(username, "username must not be null");
            return this;
        }

        public Builder email(String email) {
            this.email = Objects.requireNonNull(email, "email must not be null");
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
            return this;
        }

        public Builder zoneInfo(String zoneInfo) {
            this.zoneInfo = Objects.requireNonNull(zoneInfo, "zoneInfo must not be null");
            return this;
        }

        public Builder notificationsEnabled(boolean notificationsEnabled) {
            this.notificationsEnabled = notificationsEnabled;
            return this;
        }

        public Builder sessionThreshold(int sessionThreshold) {
            this.sessionThreshold = sessionThreshold;
            return this;
        }

        public Builder startOfDay(int startOfDay) {
            this.startOfDay = startOfDay;
            return this;
        }

        public Builder addRole(UserRole role) {
            this.roles.add(Objects.requireNonNull(role, "role must not be null"));
            return this;
        }

        public Builder roles(Set<UserRole> roles) {
            this.roles = new HashSet<>(Objects.requireNonNull(roles, "roles must not be null"));
            return this;
        }

        public User build() {
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(passwordHash, "passwordHash must not be null");
            Objects.requireNonNull(zoneInfo, "zoneInfo must not be null");
            if (roles.isEmpty())
                throw new IllegalStateException("At least one role is required");
            return new User(this);
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.decks == null)
            this.decks = new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getZoneInfo() {
        return zoneInfo;
    }

    public void setZoneInfo(String zoneInfo) {
        this.zoneInfo = zoneInfo;
    }

    public Instant getLastNotificationSent() {
        return lastNotificationSent;
    }

    public void setLastNotificationSent(Instant lastNotificationSent) {
        this.lastNotificationSent = lastNotificationSent;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public int getSessionThreshold() {
        return sessionThreshold;
    }

    public void setSessionThreshold(int sessionThreshold) {
        this.sessionThreshold = sessionThreshold;
    }

    public int getStartOfDay() {
        return startOfDay;
    }

    public void setStartOfDay(int startOfDay) {
        this.startOfDay = startOfDay;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }

    public List<Deck> getDecks() {
        return decks;
    }

    public void setDecks(List<Deck> decks) {
        this.decks = decks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", passwordHash='" + passwordHash + '\'' +
                ", lastLogin=" + lastLogin +
                ", zoneInfo='" + zoneInfo + '\'' +
                ", lastNotificationSent=" + lastNotificationSent +
                ", notificationsEnabled=" + notificationsEnabled +
                ", sessionThreshold=" + sessionThreshold +
                ", startOfDay=" + startOfDay +
                '}';
    }

    public enum UserRole {
        ROLE_USER, ROLE_ADMIN
    }
}
