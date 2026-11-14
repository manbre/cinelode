package com.cinelode.backend.domain;

import java.util.List;

/** Holds one series and its shared metadata, seasons, and episodes. */
public record Series(
        long id,
        String title,
        int year,
        String directoryPath,
        String genre,
        String actors,
        String plot,
        String runtime,
        String writer,
        String rated,
        Double imdbRating,
        Integer primetimeEmmyWins,
        MetadataStatus metadataStatus,
        List<SeriesSeason> seasons) {
}
