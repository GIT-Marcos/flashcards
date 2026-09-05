package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class AiGenerationException extends DomainException {

    public AiGenerationException(String detail) {
        super("AI generation failed: " + detail, HttpStatus.BAD_GATEWAY);
    }
}
