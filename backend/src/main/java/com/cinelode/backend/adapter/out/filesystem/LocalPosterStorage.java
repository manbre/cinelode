package com.cinelode.backend.adapter.out.filesystem;

import com.cinelode.backend.application.port.PosterStorage;
import com.cinelode.backend.domain.MovieFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores movie posters beside media files on the local filesystem. */
@Component
public class LocalPosterStorage implements PosterStorage {
    private static final Logger LOGGER = Logger.getLogger(LocalPosterStorage.class.getName());
    private static final int MAX_POSTER_BYTES = 20 * 1024 * 1024;

    private final Path mediaRoot;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Creates poster storage rooted at the configured media directory. */
    public LocalPosterStorage(@Value("${cinelode.media.root}") String mediaRoot) {
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    /** Downloads a poster beside its movie unless one already exists. */
    @Override
    public boolean downloadIfMissing(String mediaPath, String posterUrl) {
        if (posterUrl == null || posterUrl.isBlank() || "N/A".equalsIgnoreCase(posterUrl)) {
            return false;
        }

        Path poster = posterPath(mediaPath);
        if (Files.exists(poster, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        try {
            URI uri = URI.create(posterUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(uri.getHost().equalsIgnoreCase("m.media-amazon.com")
                    || uri.getHost().equalsIgnoreCase("ia.media-imdb.com"))) {
                throw new IOException("Nicht erlaubter Poster-Host");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentLength > MAX_POSTER_BYTES
                        || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    throw new IOException("Ungültige Poster-Antwort");
                }
                byte[] bytes = readBounded(body, MAX_POSTER_BYTES);
                if (bytes.length < 4) {
                    throw new IOException("Ungültige Poster-Antwort");
                }
                Files.write(poster, bytes, StandardOpenOption.CREATE_NEW);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Poster-Download unterbrochen", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Poster-Download fehlgeschlagen: " + exception.getMessage(), exception);
        }
    }

    /** Finds a readable poster beside a movie file. */
    @Override
    public Optional<Path> findPoster(String mediaPath) {
        final Path poster;
        try {
            poster = posterPath(mediaPath);
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Posterpfad außerhalb des Medienordners wurde abgewiesen.", exception);
            return Optional.empty();
        }
        return Files.isRegularFile(poster) && Files.isReadable(poster) ? Optional.of(poster) : Optional.empty();
    }

    /** Removes posters for entries that are no longer in the catalog. */
    @Override
    public void deleteOrphanedPosters(Collection<MovieFile> activeMovies, Collection<String> removedMediaPaths) {
        Set<Path> activePosters = new HashSet<>();
        for (MovieFile movie : activeMovies) {
            try {
                activePosters.add(posterPath(movie.filePath()));
            } catch (IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING, "Unsicherer aktiver Posterpfad wurde ignoriert.", exception);
            }
        }
        for (String removedPath : removedMediaPaths) {
            final Path poster;
            try {
                poster = posterPathForRemovedMedia(removedPath);
            } catch (IllegalArgumentException exception) {
                LOGGER.log(Level.WARNING, "Unsicherer verwaister Posterpfad wurde ignoriert.", exception);
                continue;
            }
            if (activePosters.contains(poster)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(poster)) {
                    LOGGER.info(() -> "Zugehöriges Poster gelöscht: " + poster);
                }
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Poster-Bereinigung fehlgeschlagen für " + poster, exception);
            }
        }
    }

    /** Builds the poster path associated with a media file. */
    private Path posterPath(String mediaPath) {
        return posterPath(mediaPath, true);
    }

    private Path posterPathForRemovedMedia(String mediaPath) {
        return posterPath(mediaPath, false);
    }

    private Path posterPath(String mediaPath, boolean requireExistingMedia) {
        Path configuredMedia = Path.of(mediaPath).toAbsolutePath().normalize();
        if (!configuredMedia.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Movie path lies outside configured media root.");
        }
        try {
            Path realRoot = mediaRoot.toRealPath();
            Path realParent = configuredMedia.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new IllegalArgumentException("Movie parent resolves outside configured media root.");
            }
            Path media = realParent.resolve(configuredMedia.getFileName());
            if (requireExistingMedia || Files.exists(media, LinkOption.NOFOLLOW_LINKS)) {
                media = media.toRealPath();
                if (!media.startsWith(realRoot) || !Files.isRegularFile(media)) {
                    throw new IllegalArgumentException("Movie path resolves outside configured media root.");
                }
            }
            return resolvePoster(media, realRoot);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Movie path cannot be resolved inside configured media root.",
                    exception);
        }
    }

    private static Path resolvePoster(Path media, Path realRoot) throws IOException {
        String filename = media.getFileName().toString();
        int extension = filename.lastIndexOf('.');
        String basename = extension < 0 ? filename : filename.substring(0, extension);
        Path poster = media.resolveSibling(basename + ".jpg").normalize();
        if (!poster.startsWith(realRoot)) {
            throw new IllegalArgumentException("Poster path lies outside configured media root.");
        }
        Path realParent = poster.getParent().toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new IllegalArgumentException("Poster path resolves outside configured media root.");
        }
        if (Files.exists(poster, LinkOption.NOFOLLOW_LINKS)
                && !poster.toRealPath().startsWith(realRoot)) {
            throw new IllegalArgumentException("Poster symlink resolves outside configured media root.");
        }
        return poster;
    }

    static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Maximum byte count must not be negative.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer, 0, Math.min(buffer.length, maxBytes - total + 1))) != -1) {
            total += bytesRead;
            if (total > maxBytes) {
                throw new IOException("Poster überschreitet die maximale Größe.");
            }
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }
}
