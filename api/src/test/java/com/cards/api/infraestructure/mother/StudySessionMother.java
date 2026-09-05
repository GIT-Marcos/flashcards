package com.cards.api.infraestructure.mother;

import com.cards.api.entity.StudySession;
import com.cards.api.entity.User;

import java.time.Instant;

public final class StudySessionMother {

    private StudySessionMother() {
    }

    /**
     * Crea una sesión básica para un usuario.
     */
    public static StudySession createForUser(User user) {
        return StudySession.builder()
                .user(user)
                .build();
    }

    /**
     * Crea una sesión con datos de finalización (útil para reportes).
     */
    public static StudySession createFinished(User user, int cards, double accuracy) {
        return StudySession.builder()
                .user(user)
                .cardsReviewed(cards)
                .accuracyRate(accuracy)
                .endTime(Instant.now())
                .build();
    }

    /**
     * Crea una StudySession con startTime específico para tests de paginación.
     */
    public static StudySession createFinishedWithTime(User user, String displayName, Instant startTime) {
        StudySession session = createForUser(user);
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusSeconds(1800)); // 30 min de duración
        session.setCardsReviewed(10);
        session.setAccuracyRate(0.85);
        // Nota: No hay campo "displayName" en la entidad, usamos logs o metadata si es necesario
        // Para este test, el nombre es solo referencial en los asserts
        return session;
    }
}
