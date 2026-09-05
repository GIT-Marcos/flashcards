package com.cards.api.infraestructure.mother;

import com.cards.api.entity.Card;
import com.cards.api.entity.Deck;
import com.cards.api.entity.User;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Object Mother para crear instancias de Card en tests.
 * Incluye helpers para la lógica SM-2 y fechas de revisión.
 */
public final class CardMother {

    private static final String DEFAULT_FRONT = "¿Qué es Java?";
    private static final String DEFAULT_BACK = "Un lenguaje de programación orientado a objetos.";
    private static final Double DEFAULT_EASINESS_FACTOR = 2.5;
    private static final Integer DEFAULT_INTERVAL = 0;
    private static final Integer DEFAULT_REPETITION = 0;

    private CardMother() {
    }

    /**
     * Crea una card nueva sin revisar (estado inicial SM-2).
     */
    public static Card createNew(Deck deck, String front, String back) {
        return Card.builder()
                .front(front)
                .back(back)
                .deck(deck)
                .nextReviewDate(Instant.now())
                .easinessFactor(DEFAULT_EASINESS_FACTOR)
                .intervalDays(DEFAULT_INTERVAL)
                .repetitionCount(DEFAULT_REPETITION)
                .build();
    }

    /**
     * Crea una card con valores SM-2 personalizados.
     */
    public static Card createWithSM2State(Deck deck, String front, String back,
                                          Double easinessFactor, Integer intervalDays,
                                          Integer repetitionCount) {
        Card card = createNew(deck, front, back);
        card.setEasinessFactor(easinessFactor);
        card.setIntervalDays(intervalDays);
        card.setRepetitionCount(repetitionCount);
        return card;
    }

    /**
     * Crea una card cuya próxima revisión es ANTES del instante dado (debería aparecer como pending).
     */
    public static Card createDueCard(Deck deck, String front, String back, Instant referenceNow) {
        Card card = createNew(deck, front, back);
        card.setNextReviewDate(referenceNow.minusSeconds(1));
        return card;
    }

    /**
     * Crea una card cuya próxima revisión es DESPUÉS del instante dado (no está pendiente aún).
     */
    public static Card createFutureCard(Deck deck, String front, String back, Instant referenceNow) {
        Card card = createNew(deck, front, back);
        card.setNextReviewDate(referenceNow.plusSeconds(3600)); // 1 hora en el futuro
        return card;
    }

    /**
     * Crea una card con nextReviewDate null para tests de edge cases.
     * Nota: En producción, @Column(nullable=false) prevendría esto,
     * pero es útil para validar comportamiento defensivo.
     */
    public static Card createWithNullReviewDate(Deck deck, String front, String back) {
        Card card = createNew(deck, front, back);
        card.setNextReviewDate(null);
        return card;
    }

    /**
     * Simula el resultado de aplicar un review con calidad específica.
     * Útil para tests que necesitan cards en estados post-review sin ejecutar la lógica completa.
     */
    public static Card createAfterReview(Deck deck, User user, String front, String back,
                                         int quality, Instant reviewInstant) {
        Card card = createNew(deck, front, back);
        card.applyReview(quality, user);
        return card;
    }

    /**
     * Calcula una fecha de revisión esperada según la lógica de normalización startOfDay.
     * Helper para aserciones en tests de Card.applyReview().
     */
    public static Instant calculateExpectedNextReview(User user, int intervalDays, Instant reviewMoment) {
        String zoneInfo = user.getZoneInfo() != null ? user.getZoneInfo() : "Europe/London";
        ZoneId zoneId = ZoneId.of(zoneInfo);

        return ZonedDateTime.ofInstant(reviewMoment, zoneId)
                .plusDays(intervalDays)
                .withHour(user.getStartOfDay())
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
    }
}
