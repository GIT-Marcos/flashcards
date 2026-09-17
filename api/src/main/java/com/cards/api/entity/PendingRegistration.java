package com.cards.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "pending_registrations")
public class PendingRegistration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "zone_info", nullable = false, length = 50)
    private String zoneInfo;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PendingRegistration() {
    }

    private PendingRegistration(Builder b) {
        this.username = b.username;
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.zoneInfo = b.zoneInfo;
        this.tokenHash = b.tokenHash;
        this.expiresAt = b.expiresAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String username;
        private String email;
        private String passwordHash;
        private String zoneInfo;
        private String tokenHash;
        private Instant expiresAt;

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

        public Builder tokenHash(String tokenHash) {
            this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            return this;
        }

        public PendingRegistration build() {
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(passwordHash, "passwordHash must not be null");
            Objects.requireNonNull(zoneInfo, "zoneInfo must not be null");
            Objects.requireNonNull(tokenHash, "tokenHash must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            return new PendingRegistration(this);
        }
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

    public String getZoneInfo() {
        return zoneInfo;
    }

    public void setZoneInfo(String zoneInfo) {
        this.zoneInfo = zoneInfo;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PendingRegistration that = (PendingRegistration) o;
        return Objects.equals(tokenHash, that.tokenHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenHash);
    }

    @Override
    public String toString() {
        return "PendingRegistration{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
