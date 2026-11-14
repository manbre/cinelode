package com.cinelode.backend.application.port;

import com.cinelode.backend.domain.MovieFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/** Defines poster download, lookup, and cleanup operations. */
public interface PosterStorage {
    /** Downloads a poster only when none is already stored. */
    boolean downloadIfMissing(String mediaPath, String posterUrl);

    /** Finds a readable poster associated with a media file. */
    Optional<Path> findPoster(String mediaPath);

    /** Deletes posters associated with entries removed from the catalog. */
    void deleteOrphanedPosters(Collection<MovieFile> activeMovies, Collection<String> removedMediaPaths);
}
