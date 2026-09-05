package com.cards.api.infraestructure.mother;

import com.cards.api.entity.Card;
import com.cards.api.entity.CardReviewLog;
import com.cards.api.entity.StudySession;
import com.cards.api.entity.User;

public final class ReviewLogMother {
    private ReviewLogMother() {}

    public static CardReviewLog create(User user, Card card, StudySession session, Integer quality) {
        return CardReviewLog.builder()
                .quality(quality)
                .card(card)
                .user(user)
                .studySession(session)
                .build();
    }
}
