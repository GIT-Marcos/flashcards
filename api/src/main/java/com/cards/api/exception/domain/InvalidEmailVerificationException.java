package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class InvalidEmailVerificationException extends DomainException {

    public InvalidEmailVerificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
