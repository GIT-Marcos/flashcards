package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class InvalidTimeZoneException extends DomainException {

    public InvalidTimeZoneException(String zoneInfo) {
        super(String.format("The time zone '%s' is not valid", zoneInfo), HttpStatus.BAD_REQUEST);
    }
}
