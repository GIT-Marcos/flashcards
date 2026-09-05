package com.cards.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.cards.api.config.encryption.AesEncryptionConverter;
import com.cards.api.util.AiProvider;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(
    name = "user_api_keys",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"})
)
public class UserApiKey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProvider provider;

    @Column(name = "key_alias", nullable = false, length = 20)
    private String keyAlias;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "encrypted_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedKey;

    protected UserApiKey() {
    }

    private UserApiKey(Builder b) {
        this.user = b.user;
        this.provider = b.provider;
        this.keyAlias = b.keyAlias;
        this.encryptedKey = b.encryptedKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private AiProvider provider;
        private String keyAlias;
        private String encryptedKey;

        private Builder() {
        }

        public Builder user(User user) {
            this.user = Objects.requireNonNull(user, "user must not be null");
            return this;
        }

        public Builder provider(AiProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider must not be null");
            return this;
        }

        public Builder keyAlias(String keyAlias) {
            this.keyAlias = Objects.requireNonNull(keyAlias, "keyAlias must not be null");
            return this;
        }

        public Builder encryptedKey(String encryptedKey) {
            this.encryptedKey = Objects.requireNonNull(encryptedKey, "encryptedKey must not be null");
            return this;
        }

        public UserApiKey build() {
            Objects.requireNonNull(user, "user must not be null");
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(keyAlias, "keyAlias must not be null");
            Objects.requireNonNull(encryptedKey, "encryptedKey must not be null");
            return new UserApiKey(this);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    @JsonIgnore
    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserApiKey that = (UserApiKey) o;
        return provider == that.provider &&
            Objects.equals(getUserIdentifier(), that.getUserIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, getUserIdentifier());
    }

    private Long getUserIdentifier() {
        return user != null ? user.getId() : null;
    }

    @Override
    public String toString() {
        return "UserApiKey{" +
            "id=" + id +
            ", provider=" + provider +
            ", keyAlias='" + keyAlias + '\'' +
            '}';
    }
}
