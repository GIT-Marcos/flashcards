package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class InvalidUnsubscribeTokenException extends DomainException {

    public InvalidUnsubscribeTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
