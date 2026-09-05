package com.cards.api.entity;

import com.cards.api.util.TimeZoneUtils;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
// Flyway es la fuente de verdad del esquema. El índice real usa LOWER(front) para unicidad
// case-insensitive; este @Index es referencia para validación (ddl-auto: validate).
@Table(
    name = "cards",
    indexes = {
        @Index(name = "uk_cards_deck_id_front_lower", columnList = "deck_id, front", unique = true)
    }
)
public class Card extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(nullable = false)
    private String front;

    @Column(nullable = false)
    private String back;

    @Column(name = "next_review_date", nullable = false)
    private Instant nextReviewDate;

    @Column(name = "interval_days")
    private Integer intervalDays = 0; // I (Intervalo actual)

    /**
     * Veces seguidas que ha respondido correctamente
     */
    @Column(name = "repetition_count")
    private Integer repetitionCount = 0;

    @Column(name = "easiness_factor")
    private Double easinessFactor = 2.5; // EF (Por defecto es 2.5)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @OneToMany(mappedBy = "card")
    private List<CardReviewLog> reviewLogs = new ArrayList<>();

    protected Card() {
    }

    private Card(Builder b) {
        this.front = b.front;
        this.back = b.back;
        this.nextReviewDate = b.nextReviewDate;
        this.intervalDays = b.intervalDays;
        this.repetitionCount = b.repetitionCount;
        this.easinessFactor = b.easinessFactor;
        this.deck = b.deck;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String front;
        private String back;
        private Instant nextReviewDate;
        private Integer intervalDays = 0;
        private Integer repetitionCount = 0;
        private Double easinessFactor = 2.5;
        private Deck deck;

        private Builder() {
        }

        public Builder front(String front) {
            this.front = Objects.requireNonNull(front, "front must not be null");
            return this;
        }

        public Builder back(String back) {
            this.back = Objects.requireNonNull(back, "back must not be null");
            return this;
        }

        public Builder nextReviewDate(Instant nextReviewDate) {
            this.nextReviewDate = nextReviewDate;
            return this;
        }

        public Builder intervalDays(Integer intervalDays) {
            this.intervalDays = intervalDays;
            return this;
        }

        public Builder repetitionCount(Integer repetitionCount) {
            this.repetitionCount = repetitionCount;
            return this;
        }

        public Builder easinessFactor(Double easinessFactor) {
            this.easinessFactor = easinessFactor;
            return this;
        }

        public Builder deck(Deck deck) {
            this.deck = deck;
            return this;
        }

        public Card build() {
            Objects.requireNonNull(front, "front must not be null");
            Objects.requireNonNull(back, "back must not be null");
            return new Card(this);
        }
    }

    public void applyReview(int quality, User user) {
        applyReview(quality, user, Instant.now());
    }

    public void applyReview(int quality, User user, Instant reviewMoment) {
        // 1. Calcular el nuevo EF (pero NO actualizar el campo todavía:
        //    SM-2 original usa el EF previo para el intervalo cuando n >= 2)
        double newEf = this.easinessFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));

        // 2. Calcular el nuevo intervalo y actualizar contador (usa EF previo)
        if (quality < 3) {
            this.intervalDays = 1;
            this.repetitionCount = 0;
        } else {
            if (this.repetitionCount == 0) {
                this.intervalDays = 1;
            } else if (this.repetitionCount == 1) {
                this.intervalDays = 6;
            } else {
                this.intervalDays = (int) Math.round(this.intervalDays * this.easinessFactor);
            }
            this.repetitionCount++;
        }

        // 3. Actualizar el EF (después del cálculo del intervalo)
        this.easinessFactor = Math.max(1.3, newEf);

        // 4. Calcular fecha de próxima revisión
        this.nextReviewDate = calculateNextReview(user, this.intervalDays, reviewMoment);
    }

    /**
     * Calcula el momento exacto en que vencerá la tarjeta.
     * Si un usuario en Madrid (UTC+2 en verano) repasa una tarjeta un lunes a las 10:00 PM y el algoritmo
     * decide que debe repasarla en 1 día:
     * <ul>
     *     <li>1. Lunes 10:00 PM + 1 día = martes 10:00 PM.</li>
     *     <li>2. Normalización: Se cambia a las 4:00 AM del martes.</li>
     *     <li>3. Resultado: La tarjeta aparecerá como "Pendiente" desde el martes a las 4:00 AM (hora de Madrid),
     *     dándole el martes entero para repasarla.</li>
     * </ul>
     */
    private Instant calculateNextReview(User user, int intervalDays, Instant reviewMoment) {
        ZoneId zoneId = TimeZoneUtils.parseOrFallback(user.getZoneInfo(), ZoneId.of("UTC"));
        ZonedDateTime nowUser = ZonedDateTime.ofInstant(reviewMoment, zoneId);
        return nowUser
            .plusDays(intervalDays)
            .withHour(user.getStartOfDay())
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant();
    }

    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    @PrePersist
    public void prePersist() {
        if (this.reviewLogs == null)
            this.reviewLogs = new ArrayList<>();
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

    public String getFront() {
        return front;
    }

    public void setFront(String front) {
        this.front = front;
    }

    public String getBack() {
        return back;
    }

    public void setBack(String back) {
        this.back = back;
    }

    public Instant getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(Instant nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public Integer getIntervalDays() {
        return this.intervalDays;
    }

    public void setIntervalDays(Integer intervalDays) {
        this.intervalDays = intervalDays;
    }

    public Integer getRepetitionCount() {
        return repetitionCount;
    }

    public void setRepetitionCount(Integer repetitionCount) {
        this.repetitionCount = repetitionCount;
    }

    public Double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(Double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public Deck getDeck() {
        return deck;
    }

    public List<CardReviewLog> getReviewLogs() {
        return reviewLogs;
    }

    public void setReviewLogs(List<CardReviewLog> reviewLogs) {
        this.reviewLogs = reviewLogs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(front, card.front) &&
            Objects.equals(getDeckIdentifier(), card.getDeckIdentifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(front, getDeckIdentifier());
    }

    private Long getDeckIdentifier() {
        return deck != null ? deck.getId() : null;
    }

    @Override
    public String toString() {
        return "Card{" +
            "id=" + id +
            ", front='" + front + '\'' +
            ", back='" + back + '\'' +
            ", nextReviewDate=" + nextReviewDate +
            ", intervalDays=" + this.intervalDays +
            ", repetitionCount=" + repetitionCount +
            ", easinessFactor=" + easinessFactor +
            '}';
    }
}
