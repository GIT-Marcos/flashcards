package com.cards.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "card_review_log")
public class CardReviewLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer quality = 0;

    @Column(name = "easiness_factor", nullable = false)
    private Double easinessFactor = 2.5; // EF (Por defecto es 2.5)

    /** Se calcula a partir de la quality */
    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 0; // El intervalo 'I' calculado (ej. 6 días)

    @Column(name = "repetition_count", nullable = false)
    private Integer repetitionCount; // El número de repetición 'n' alcanzado

    /** Fecha de la próxima, se calcula a partir de la fecha de creación y los días de intervalo */
    @Column(name = "next_review_date", nullable = false)
    private Instant nextReviewDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession studySession;

    protected CardReviewLog() {
    }

    private CardReviewLog(Builder b) {
        this.quality = b.quality != null ? b.quality : 0;
        this.easinessFactor = b.easinessFactor != null ? b.easinessFactor : 2.5;
        this.intervalDays = b.intervalDays != null ? b.intervalDays : 0;
        this.repetitionCount = b.repetitionCount;
        this.nextReviewDate = b.nextReviewDate;
        this.card = b.card;
        this.user = b.user;
        this.studySession = b.studySession;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer quality;
        private Card card;
        private User user;
        private StudySession studySession;
        private Double easinessFactor;
        private Integer intervalDays;
        private Integer repetitionCount;
        private Instant nextReviewDate;

        public Builder quality(Integer quality) {
            this.quality = quality;
            return this;
        }

        public Builder card(Card card) {
            this.card = card;
            if (card != null) {
                this.easinessFactor = card.getEasinessFactor();
                this.intervalDays = card.getIntervalDays();
                this.repetitionCount = card.getRepetitionCount();
                this.nextReviewDate = card.getNextReviewDate();
            }
            return this;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder studySession(StudySession studySession) {
            this.studySession = studySession;
            return this;
        }

        public Builder easinessFactor(Double easinessFactor) {
            this.easinessFactor = easinessFactor;
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

        public Builder nextReviewDate(Instant nextReviewDate) {
            this.nextReviewDate = nextReviewDate;
            return this;
        }

        public CardReviewLog build() {
            return new CardReviewLog(this);
        }
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setStudySession(StudySession studySession) {
        this.studySession = studySession;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getQuality() {
        return quality;
    }

    public void setQuality(Integer quality) {
        this.quality = quality;
    }

    public Double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(Double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public Integer getIntervalDays() {
        return intervalDays;
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

    public Instant getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(Instant nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public Card getCard() {
        return card;
    }

    public User getUser() {
        return user;
    }

    public StudySession getStudySession() {
        return studySession;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardReviewLog that = (CardReviewLog) o;
        if (id == null && that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "CardReviewLog{" +
                "id=" + id +
                ", quality=" + quality +
                ", easinessFactor=" + easinessFactor +
                ", intervalDays=" + intervalDays +
                ", repetitionCount=" + repetitionCount +
                ", nextReviewDate=" + nextReviewDate +
                '}';
    }
}
