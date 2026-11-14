package com.cinelode.backend.domain;

import java.util.List;

/** Represents a season and its locally stored poster and episodes. */
public record SeriesSeason(long id, int seasonNumber, String posterPath, List<SeriesEpisode> episodes) {
}
