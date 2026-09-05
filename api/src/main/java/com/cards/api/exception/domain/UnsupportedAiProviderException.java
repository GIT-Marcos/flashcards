package com.cards.api.exception.domain;

import com.cards.api.util.AiProvider;
import org.springframework.http.HttpStatus;

public class UnsupportedAiProviderException extends DomainException {

    public UnsupportedAiProviderException(AiProvider provider) {
        super("No implementation available for provider: " + provider, HttpStatus.BAD_REQUEST);
    }
}
