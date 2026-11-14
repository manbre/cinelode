package com.cinelode.backend.domain;

/** Holds provider-independent details retrieved for a movie. */
public record MovieMetadata(
        String genre,
        String director,
        String actors,
        String plot,
        String runtime,
        String writer,
        String rated,
        Double imdbRating,
        Integer oscarWins,
        String posterUrl) {
}
