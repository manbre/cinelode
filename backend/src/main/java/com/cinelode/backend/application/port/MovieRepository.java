package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.Movie;
import com.cinelode.backend.domain.MovieFile;
import com.cinelode.backend.domain.MovieMetadata;

import java.util.List;
import java.util.Optional;

/** Defines movie persistence operations required by the application. */
public interface MovieRepository {
    /** Synchronizes stored movies and returns paths removed from the catalog. */
    List<String> synchronize(List<MovieFile> discoveredMovies);

    /** Finds movies whose metadata lookup has not completed successfully. */
    List<Movie> findMoviesWithIncompleteMetadata();

    /** Loads all movies from the catalog. */
    List<Movie> findAll();

    /** Looks up a movie by its identifier. */
    Optional<Movie> findById(long id);

    /** Marks a metadata lookup with no matching movie. */
    void markMetadataNotFound(long id);

    /** Marks a metadata lookup that failed. */
    void markMetadataError(long id);

    /** Saves fetched metadata for a movie. */
    void saveMetadata(long id, MovieMetadata metadata);

    void savePlaybackPosition(long id, double position);
}
