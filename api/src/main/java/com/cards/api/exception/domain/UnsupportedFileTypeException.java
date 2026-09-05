package com.cards.api.exception.domain;

import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends DomainException {

    public UnsupportedFileTypeException(String extension) {
        super("Unsupported file type: '" + extension + "'. Only .txt and .pdf are supported.", HttpStatus.BAD_REQUEST);
    }
}
