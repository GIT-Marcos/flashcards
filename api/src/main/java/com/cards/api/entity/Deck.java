package com.cards.api.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
// Flyway es la fuente de verdad del esquema. El índice real usa LOWER(name) para unicidad
// case-insensitive; este @Index es referencia para validación (ddl-auto: validate).
@Table(
        name = "decks",
        indexes = @Index(name = "uk_decks_user_id_name_lower", columnList = "user_id, name", unique = true)
)
public class Deck extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "has_pending_cards", nullable = false)
    private Boolean hasPendingCards = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @OneToMany(mappedBy = "deck", orphanRemoval = true)
    private Set<Card> cards = new HashSet<>();

    protected Deck() {
    }

    private Deck(Builder b) {
        this.name = b.name;
        this.hasPendingCards = b.hasPendingCards;
        this.user = b.user;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Boolean hasPendingCards = false;
        private User user;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
            return this;
        }

        public Builder hasPendingCards(Boolean hasPendingCards) {
            this.hasPendingCards = hasPendingCards;
            return this;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Deck build() {
            Objects.requireNonNull(name, "name must not be null");
            return new Deck(this);
        }
    }

    public void setUser(User user) {
        this.user = user;
    }

    @PrePersist
    public void prePersist() {
        if (this.cards == null)
            this.cards = new HashSet<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getHasPendingCards() {
        return hasPendingCards;
    }

    public void setHasPendingCards(Boolean hasPendingCards) {
        this.hasPendingCards = hasPendingCards;
    }

    public User getUser() {
        return user;
    }

    public Set<Card> getCards() {
        return cards;
    }

    public void setCards(Set<Card> cards) {
        this.cards = cards;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deck deck = (Deck) o;
        return Objects.equals(name, deck.name) &&
                Objects.equals(getUserIdentifier(), deck.getUserIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, getUserIdentifier());
    }

    private String getUserIdentifier() {
        return user != null ? user.getUsername() : null;
    }

    @Override
    public String toString() {
        return "Deck{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", hasPendingCards=" + hasPendingCards +
                '}';
    }
}
