package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class InvalidReviewDateException extends DomainException {

    public InvalidReviewDateException() {
        super(
                "Card is not yet due for review",
                HttpStatus.BAD_REQUEST
        );
    }
}