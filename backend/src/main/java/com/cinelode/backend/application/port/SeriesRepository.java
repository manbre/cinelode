package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.Series;
import com.cinelode.backend.domain.SeriesEpisode;
import com.cinelode.backend.domain.SeriesFile;
import com.cinelode.backend.domain.SeriesMetadata;

import java.util.List;
import java.util.Optional;

/** Defines persistence operations for series, seasons, and episodes. */
public interface SeriesRepository {
    void synchronize(List<SeriesFile> discoveredSeries);

    List<Series> findAll();

    List<Series> findSeriesWithIncompleteMetadata();

    Optional<SeriesEpisode> findEpisodeById(long id);

    Optional<String> findSeasonPosterPath(long seasonId);

    void saveMetadata(long seriesId, SeriesMetadata metadata);

    void markMetadataStatus(long seriesId, MetadataStatus status);
}
