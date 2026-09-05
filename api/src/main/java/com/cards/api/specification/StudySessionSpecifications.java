package com.cards.api.specification;

import com.cards.api.entity.StudySession;
import org.springframework.data.jpa.domain.Specification;

public class StudySessionSpecifications {

    private StudySessionSpecifications() {
    }

    public static Specification<StudySession> hasUser(Long userId) {
        return (root, query, cb) ->
                cb.equal(root.get("user").get("id"), userId);
    }
}
