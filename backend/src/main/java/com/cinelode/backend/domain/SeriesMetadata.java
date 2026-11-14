package com.cinelode.backend.domain;

/** Holds provider-independent metadata shared by every episode in a series. */
public record SeriesMetadata(
        String genre,
        String actors,
        String plot,
        String runtime,
        String writer,
        String rated,
        Double imdbRating,
        Integer primetimeEmmyWins) {
}
