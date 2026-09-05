package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DuplicatedCardException extends DomainException {

    public DuplicatedCardException(String cardFront) {
        super(
                "The card with the front '" + cardFront + "' already exist",
                HttpStatus.CONFLICT
        );
    }
}
