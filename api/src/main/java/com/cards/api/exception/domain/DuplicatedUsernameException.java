package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DuplicatedUsernameException extends DomainException {

    public DuplicatedUsernameException(String username) {
        super(
                "Username '" + username + "' already taken",
                HttpStatus.CONFLICT
        );
    }
}
