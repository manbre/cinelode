package com.cinelode.backend.domain;

/** Holds a movie and its catalogued metadata. */
public record Movie(
        Long id,
        String title,
        Integer year,
        String filePath,
        String franchise,
        String genre,
        String director,
        String actors,
        String plot,
        String runtime,
        String writer,
        String rated,
        Double imdbRating,
        Integer oscarWins,
        MetadataStatus metadataStatus,
        double playbackPosition) {
    public Movie(Long id, String title, Integer year, String filePath, String franchise,
            String genre, String director, String actors, String plot, String runtime, String writer,
            String rated, Double imdbRating, Integer oscarWins, MetadataStatus metadataStatus) {
        this(id, title, year, filePath, franchise, genre, director, actors, plot, runtime, writer,
                rated, imdbRating, oscarWins, metadataStatus, 0);
    }
}
