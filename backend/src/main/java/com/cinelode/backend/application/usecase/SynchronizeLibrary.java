package com.cinelode.backend.application.usecase;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.application.port.MovieMetadataProvider;
import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.application.port.PosterStorage;
import com.cinelode.backend.domain.Movie;
import com.cinelode.backend.domain.MovieFile;
import com.cinelode.backend.domain.MovieMetadata;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Synchronizes discovered movie files and enriches movies with provider metadata. */
public class SynchronizeLibrary {
    private static final int MAX_METADATA_ATTEMPTS = 3;
    private static final int MAX_CONCURRENT_METADATA_LOOKUPS = 4;
    private static final Logger LOGGER = Logger.getLogger(SynchronizeLibrary.class.getName());

    private final MovieCatalog catalog;
    private final MovieRepository movies;
    private final MovieMetadataProvider metadataProvider;
    private final PosterStorage posters;

    /** Creates the use case with its required application ports. */
    public SynchronizeLibrary(
            MovieCatalog catalog,
            MovieRepository movies,
            MovieMetadataProvider metadataProvider,
            PosterStorage posters) {
        this.catalog = catalog;
        this.movies = movies;
        this.metadataProvider = metadataProvider;
        this.posters = posters;
    }

    /** Scans the catalog and retries metadata lookup until complete or attempts are exhausted. */
    public int execute() {
        List<MovieFile> discovered = catalog.scan();
        List<String> removedPaths = movies.synchronize(discovered);
        posters.deleteOrphanedPosters(discovered, removedPaths);

        LOGGER.info(() -> "Cinelode: " + discovered.size() + " Filmeinträge synchronisiert.");
        try (ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_METADATA_LOOKUPS)) {
            for (int attempt = 1; attempt <= MAX_METADATA_ATTEMPTS; attempt++) {
                List<Movie> incomplete = movies.findMoviesWithIncompleteMetadata();
                if (incomplete.isEmpty()) {
                    return discovered.size();
                }
                int currentAttempt = attempt;
                LOGGER.info(() -> "Metadatenversuch " + currentAttempt + "/" + MAX_METADATA_ATTEMPTS
                        + " für " + incomplete.size() + " Filme.");
                enrichMetadataConcurrently(incomplete, executor);
            }

            List<Movie> incomplete = movies.findMoviesWithIncompleteMetadata();
            if (!incomplete.isEmpty()) {
                String titles = incomplete.stream()
                        .map(movie -> movie.title() + " (" + movie.year() + ")")
                        .toList()
                        .toString();
                throw new IncompleteMetadataException("Metadaten nach " + MAX_METADATA_ATTEMPTS
                        + " Versuchen nicht vollständig: " + titles);
            }
        }
        return discovered.size();
    }

    /** Fetches metadata for one pass concurrently while limiting provider requests. */
    private void enrichMetadataConcurrently(List<Movie> incomplete, ExecutorService executor) {
        List<Callable<Void>> tasks = incomplete.stream()
                .<Callable<Void>>map(movie -> () -> {
                    enrichMetadata(movie);
                    return null;
                })
                .toList();
        try {
            for (Future<Void> result : executor.invokeAll(tasks)) {
                result.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Metadatenabruf wurde unterbrochen.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Metadatenverarbeitung ist fehlgeschlagen.", exception.getCause());
        }
    }

    /** Fetches and stores metadata for one movie. */
    private void enrichMetadata(Movie movie) {
        try {
            Optional<MovieMetadata> metadata = metadataProvider.findByTitleAndYear(movie.title(), movie.year());
            if (metadata.isEmpty()) {
                movies.markMetadataNotFound(movie.id());
                return;
            }

            MovieMetadata found = metadata.get();
            movies.saveMetadata(movie.id(), found);
            try {
                if (posters.downloadIfMissing(movie.filePath(), found.posterUrl())) {
                    LOGGER.info(() -> "Poster gespeichert: " + movie.title());
                }
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Posterfehler für " + movie.title(), exception);
            }
        } catch (RuntimeException exception) {
            movies.markMetadataError(movie.id());
            LOGGER.log(Level.WARNING, "Metadatenfehler für " + movie.title(), exception);
        }
    }
}
