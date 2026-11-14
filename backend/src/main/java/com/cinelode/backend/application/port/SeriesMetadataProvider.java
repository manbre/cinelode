package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.SeriesMetadata;

import java.util.Optional;

/** Defines metadata lookup for a series title and starting year. */
public interface SeriesMetadataProvider {
    Optional<SeriesMetadata> findByTitleAndYear(String title, int year);
}
