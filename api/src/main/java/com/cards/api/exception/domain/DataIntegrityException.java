package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DataIntegrityException extends DomainException {

    public DataIntegrityException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
