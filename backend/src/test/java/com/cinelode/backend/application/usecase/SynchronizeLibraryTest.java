package com.cinelode.backend.application.usecase;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.application.port.MovieMetadataProvider;
import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.application.port.PosterStorage;
import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.Movie;
import com.cinelode.backend.domain.MovieFile;
import com.cinelode.backend.domain.MovieMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizeLibraryTest {
    /** Synchronizes discovered files and saves metadata and posters for pending movies. */
    @Test
    void synchronizesDiscoveredMoviesAndEnrichesPendingMetadata() {
        MovieFile discoveredMovie = new MovieFile("Example Film", 2024, "/movies/Example Film (2024).mkv", null);
        Movie movie = new Movie(1L, "Example Film", 2024, discoveredMovie.filePath(), null,
                null, null, null, null, null, null, null, null, null, MetadataStatus.PENDING);
        FakeRepository repository = new FakeRepository(movie);
        FakeMetadataProvider metadataProvider = new FakeMetadataProvider(
                Optional.of(new MovieMetadata("Drama", "Director", "Actor", "Plot", "136 min",
                        "Writer", "PG", 8.2, 5, "https://m.media-amazon.com/poster.jpg")));
        FakePosterStorage posterStorage = new FakePosterStorage();
        SynchronizeLibrary useCase = new SynchronizeLibrary(
                new FakeCatalog(List.of(discoveredMovie)), repository, metadataProvider, posterStorage);

        int count = useCase.execute();

        assertEquals(1, count);
        assertEquals(List.of(discoveredMovie), repository.synchronizedMovies);
        assertEquals(1L, repository.savedMetadataMovieId);
        assertEquals("Drama", repository.savedMetadata.genre());
        assertEquals("136 min", repository.savedMetadata.runtime());
        assertEquals(5, repository.savedMetadata.oscarWins());
        assertEquals(1, posterStorage.downloadedCount);
        assertEquals(1, posterStorage.cleanupCount);
    }

    /** Retries a missing metadata result three times and fails synchronization. */
    @Test
    void retriesMovieWithoutMetadataAndFailsAfterThreeAttempts() {
        Movie movie = new Movie(2L, "Unknown", 2024, "/movies/Unknown (2024).mkv", null,
                null, null, null, null, null, null, null, null, null, MetadataStatus.PENDING);
        FakeRepository repository = new FakeRepository(movie);
        int[] calls = {0};
        SynchronizeLibrary useCase = new SynchronizeLibrary(
                new FakeCatalog(List.of()), repository, (title, year) -> {
                    calls[0]++;
                    return Optional.empty();
                }, new FakePosterStorage());

        IncompleteMetadataException exception = assertThrows(IncompleteMetadataException.class, useCase::execute);

        assertEquals(3, calls[0]);
        assertEquals(2L, repository.notFoundMovieId);
        assertTrue(exception.getMessage().contains("Unknown (2024)"));
    }

    /** Retries incomplete metadata and completes the movie on the final allowed attempt. */
    @Test
    void retriesIncompleteMetadataUntilItBecomesComplete() {
        MovieFile discoveredMovie = new MovieFile("Retry Film", 2024, "/movies/Retry Film (2024).mkv", null);
        Movie movie = new Movie(3L, "Retry Film", 2024, discoveredMovie.filePath(), null,
                null, null, null, null, null, null, null, null, null, MetadataStatus.PENDING);
        FakeRepository repository = new FakeRepository(movie);
        int[] calls = {0};
        MovieMetadata metadata = new MovieMetadata("Drama", "Director", "Actor", "Plot", "90 min",
                "Writer", "PG", 7.5, 1, null);
        SynchronizeLibrary useCase = new SynchronizeLibrary(
                new FakeCatalog(List.of(discoveredMovie)),
                repository,
                (title, year) -> ++calls[0] < 3 ? Optional.empty() : Optional.of(metadata),
                new FakePosterStorage());

        useCase.execute();

        assertEquals(3, calls[0]);
        assertEquals(3L, repository.savedMetadataMovieId);
    }

    /** Retries error records later without repeating terminal not-found results. */
    @Test
    void retryErroredMetadataRetriesOnlyFailedLookups() {
        Movie failedMovie = new Movie(6L, "Retry After Network", 2024,
                "/movies/Retry After Network (2024).mkv", null,
                null, null, null, null, null, null, null, null, null, MetadataStatus.ERROR);
        Movie notFoundMovie = new Movie(7L, "Still Unknown", 2024,
                "/movies/Still Unknown (2024).mkv", null,
                null, null, null, null, null, null, null, null, null, MetadataStatus.NOT_FOUND);
        FakeRepository repository = new FakeRepository(List.of(failedMovie, notFoundMovie));
        AtomicInteger calls = new AtomicInteger();
        MovieMetadata metadata = new MovieMetadata("Drama", "Director", "Actor", "Plot", "90 min",
                "Writer", "PG", 7.5, 1, null);
        SynchronizeLibrary useCase = new SynchronizeLibrary(
                new FakeCatalog(List.of()), repository, (title, year) -> {
                    calls.incrementAndGet();
                    return Optional.of(metadata);
                }, new FakePosterStorage());

        int retried = useCase.retryErroredMetadata();

        assertEquals(1, retried);
        assertEquals(1, calls.get());
        assertEquals(6L, repository.savedMetadataMovieId);
    }

    /** Fetches different movies in the same pass concurrently. */
    @Test
    void fetchesMetadataConcurrentlyWithinEachPass() {
        List<Movie> movies = List.of(
                new Movie(4L, "First", 2024, "/movies/First (2024).mkv", null,
                        null, null, null, null, null, null, null, null, null, MetadataStatus.PENDING),
                new Movie(5L, "Second", 2024, "/movies/Second (2024).mkv", null,
                        null, null, null, null, null, null, null, null, null, MetadataStatus.PENDING));
        FakeRepository repository = new FakeRepository(movies);
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger activeLookups = new AtomicInteger();
        AtomicInteger maxConcurrentLookups = new AtomicInteger();
        MovieMetadata metadata = new MovieMetadata("Drama", "Director", "Actor", "Plot", "90 min",
                "Writer", "PG", 7.5, 1, null);
        SynchronizeLibrary useCase = new SynchronizeLibrary(
                new FakeCatalog(List.of()),
                repository,
                (title, year) -> {
                    int active = activeLookups.incrementAndGet();
                    maxConcurrentLookups.accumulateAndGet(active, Math::max);
                    bothStarted.countDown();
                    try {
                        if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("Both lookups should run concurrently");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Test lookup interrupted.", exception);
                    } finally {
                        activeLookups.decrementAndGet();
                    }
                    return Optional.of(metadata);
                },
                new FakePosterStorage());

        assertEquals(0, useCase.execute());

        assertEquals(2, maxConcurrentLookups.get());
    }

    private record FakeCatalog(List<MovieFile> movies) implements MovieCatalog {
        /** Returns the test catalog entries. */
        @Override
        public List<MovieFile> scan() {
            return movies;
        }

        /** Resolves a test media path directly. */
        @Override
        public Path resolveMediaFile(String filePath) {
            return Path.of(filePath);
        }

        /** Returns a fixed external URL for test entries. */
        @Override
        public URI readExternalUrl(String filePath) {
            return URI.create("https://movies.example/watch");
        }
    }

    private static final class FakeRepository implements MovieRepository {
        private final List<Movie> pendingMovies;
        private final Set<Long> completedMovieIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private List<MovieFile> synchronizedMovies;
        private long savedMetadataMovieId;
        private MovieMetadata savedMetadata;
        private long notFoundMovieId;

        /** Creates a repository fake with one pending movie. */
        private FakeRepository(Movie pendingMovie) {
            this(List.of(pendingMovie));
        }

        /** Creates a repository fake with pending movies. */
        private FakeRepository(List<Movie> pendingMovies) {
            this.pendingMovies = pendingMovies;
        }

        /** Captures the discovered entries and reports one removed test path. */
        @Override
        public List<String> synchronize(List<MovieFile> discoveredMovies) {
            synchronizedMovies = discoveredMovies;
            return List.of("/movies/removed.mkv");
        }

        /** Returns the configured pending movie. */
        @Override
        public List<Movie> findMoviesWithIncompleteMetadata() {
            return pendingMovies.stream()
                    .filter(movie -> !completedMovieIds.contains(movie.id()))
                    .toList();
        }

        /** Returns no movies for this test fake. */
        @Override
        public List<Movie> findAll() {
            return List.of();
        }

        /** Returns no movie for this test fake. */
        @Override
        public Optional<Movie> findById(long id) {
            return Optional.empty();
        }

        /** Records that metadata was not found. */
        @Override
        public void markMetadataNotFound(long id) {
            notFoundMovieId = id;
            completedMovieIds.remove(id);
        }

        /** Fails if the test unexpectedly marks metadata as erroneous. */
        @Override
        public void markMetadataError(long id) {
            notFoundMovieId = -id;
        }

        /** Captures metadata saved by the use case. */
        @Override
        public void saveMetadata(long id, MovieMetadata metadata) {
            savedMetadataMovieId = id;
            savedMetadata = metadata;
            completedMovieIds.add(id);
        }

        @Override
        public void savePlaybackPosition(long id, double position) {
        }
    }

    private static final class FakeMetadataProvider implements MovieMetadataProvider {
        private final Optional<MovieMetadata> metadata;

        /** Creates a metadata provider fake with a fixed result. */
        private FakeMetadataProvider(Optional<MovieMetadata> metadata) {
            this.metadata = metadata;
        }

        /** Returns the configured metadata result. */
        @Override
        public Optional<MovieMetadata> findByTitleAndYear(String title, int year) {
            return metadata;
        }
    }

    private static final class FakePosterStorage implements PosterStorage {
        private int cleanupCount;
        private int downloadedCount;

        /** Records poster download requests without accessing the network. */
        @Override
        public synchronized boolean downloadIfMissing(String mediaPath, String posterUrl) {
            downloadedCount++;
            return true;
        }

        /** Returns no stored poster for this test fake. */
        @Override
        public Optional<Path> findPoster(String mediaPath) {
            return Optional.empty();
        }

        /** Records orphan cleanup requests without deleting files. */
        @Override
        public synchronized void deleteOrphanedPosters(
                java.util.Collection<MovieFile> activeMovies, java.util.Collection<String> removedMediaPaths) {
            cleanupCount++;
        }
    }
}
