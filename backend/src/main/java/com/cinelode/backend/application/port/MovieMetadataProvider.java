package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.MovieMetadata;

import java.util.Optional;

/** Defines the lookup of movie details from a metadata provider. */
public interface MovieMetadataProvider {
    /** Finds metadata matching the supplied title and year. */
    Optional<MovieMetadata> findByTitleAndYear(String title, int year);
}
