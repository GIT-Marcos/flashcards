package com.cards.api.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "study_sessions"
)
public class StudySession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "start_time", nullable = false)
    private Instant startTime = Instant.now();

    @Column(name = "end_time", nullable = false)
    private Instant endTime = Instant.now();

    @Column(name = "cards_reviewed", nullable = false)
    private Integer cardsReviewed = 0;

    @Column(name = "accuracy_rate", nullable = false)
    private Double accuracyRate = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "studySession", orphanRemoval = true)
    private Set<CardReviewLog> logs = new HashSet<>();

    protected StudySession() {
    }

    private StudySession(Builder b) {
        this.user = b.user;
        this.startTime = b.startTime;
        this.endTime = b.endTime;
        this.cardsReviewed = b.cardsReviewed;
        this.accuracyRate = b.accuracyRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private Integer cardsReviewed = 0;
        private Double accuracyRate = 0.0;

        private Builder() {
        }

        public Builder user(User user) {
            this.user = Objects.requireNonNull(user, "user must not be null");
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder cardsReviewed(Integer cardsReviewed) {
            this.cardsReviewed = cardsReviewed;
            return this;
        }

        public Builder accuracyRate(Double accuracyRate) {
            this.accuracyRate = accuracyRate;
            return this;
        }

        public StudySession build() {
            Objects.requireNonNull(user, "user must not be null");
            return new StudySession(this);
        }
    }

    public void updateMetrics(int quality, Instant now) {
        this.cardsReviewed++;
        this.endTime = now;

        double isSuccess = (quality >= 3) ? 1.0 : 0.0;

        double rawAccuracy = ((this.accuracyRate * (this.cardsReviewed - 1)) + isSuccess) / this.cardsReviewed;

        this.accuracyRate = BigDecimal.valueOf(rawAccuracy)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @PrePersist
    public void prePersist() {
        if (this.logs == null)
            this.logs = new HashSet<>();
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

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Integer getCardsReviewed() {
        return cardsReviewed;
    }

    public void setCardsReviewed(Integer cardsReviewed) {
        this.cardsReviewed = cardsReviewed;
    }

    public Double getAccuracyRate() {
        return accuracyRate;
    }

    public void setAccuracyRate(Double accuracyRate) {
        this.accuracyRate = accuracyRate;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<CardReviewLog> getLogs() {
        return logs;
    }

    public void setLogs(Set<CardReviewLog> logs) {
        this.logs = logs;
    }

    // Si la entidad no tiene clave de negocio natural, conviene usar el ID y asegurarse de no agregarlas
    //  a Set o Map antes de que se les asigne un ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StudySession that = (StudySession) o;
        if (id == null && that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "StudySession{" +
                "id=" + id +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", cardsReviewed=" + cardsReviewed +
                ", accuracyRate=" + accuracyRate +
                '}';
    }
}
