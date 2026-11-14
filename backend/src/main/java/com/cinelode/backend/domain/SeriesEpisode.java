package com.cinelode.backend.domain;

/** Represents one episode file stored in the series catalog. */
public record SeriesEpisode(long id, int episodeNumber, String filePath) {
}
