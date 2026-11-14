package com.cinelode.backend.adapter.out.omdb;

/** Signals a failure while querying or interpreting an OMDb response. */
public class OmdbException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Creates an OMDb error with a descriptive message. */
    public OmdbException(String message) {
        super(message);
    }

    /** Creates an OMDb error with its underlying cause. */
    public OmdbException(String message, Throwable cause) {
        super(message, cause);
    }
}
