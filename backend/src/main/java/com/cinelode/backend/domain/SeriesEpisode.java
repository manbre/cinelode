package com.cinelode.backend.domain;

/** Represents one episode file stored in the series catalog. */
public record SeriesEpisode(long id, int episodeNumber, String filePath, double playbackPosition) {
    public SeriesEpisode(long id, int episodeNumber, String filePath) {
        this(id, episodeNumber, filePath, 0);
    }
}
