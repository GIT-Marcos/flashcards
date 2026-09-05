package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DuplicatedDeckException extends DomainException {

    public DuplicatedDeckException(String deckName) {
        super(
                "The deck '" + deckName + "' already exists",
                HttpStatus.CONFLICT
        );
    }
}
