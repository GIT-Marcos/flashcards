package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DuplicatedApiKeyException extends DomainException {

    public DuplicatedApiKeyException(String provider) {
        super(
            "An API key for '" + provider + "' already exists",
            HttpStatus.CONFLICT
        );
    }
}
