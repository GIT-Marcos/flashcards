package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class InvalidResetPasswordTokenException extends DomainException {

    public InvalidResetPasswordTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
