package com.cinelode.backend.domain;

/** Describes the current state of a movie's metadata lookup. */
public enum MetadataStatus {
    PENDING,
    COMPLETE,
    NOT_FOUND,
    ERROR
}
