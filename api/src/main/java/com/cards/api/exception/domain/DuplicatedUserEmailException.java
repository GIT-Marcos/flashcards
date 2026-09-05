package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class DuplicatedUserEmailException extends DomainException {

    public DuplicatedUserEmailException(String userEmail) {
        super(
                "The email address '" + userEmail + "' is already registered",
                HttpStatus.CONFLICT
        );

    }
}
