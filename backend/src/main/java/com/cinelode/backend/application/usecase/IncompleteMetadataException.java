package com.cinelode.backend.application.usecase;

/** Signals that some movies still lack complete metadata after all retry passes. */
public class IncompleteMetadataException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public IncompleteMetadataException(String message) {
        super(message);
    }
}
