package com.cinelode.backend.adapter.out.filesystem;

import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.MovieFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Discovers supported movie files and reads external movie links locally. */
@Component
public class LocalMovieCatalog implements MovieCatalog {
    private static final Pattern FILE_NAME =
            Pattern.compile("^(?:\\d+_)?(.+) \\((\\d{4})\\)\\.(mkv|txt)$", Pattern.CASE_INSENSITIVE);

    private final Path mediaRoot;

    /** Creates a catalog rooted at the configured media directory. */
    public LocalMovieCatalog(@Value("${cinelode.media.root}") String mediaRoot) {
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    /** Finds supported movie files and prefers MKV over a matching TXT entry. */
    @Override
    public List<MovieFile> scan() {
        if (!Files.isDirectory(mediaRoot) || !Files.isReadable(mediaRoot)) {
            throw new IllegalStateException("Medienordner fehlt oder ist nicht lesbar: " + mediaRoot);
        }

        Map<String, MovieFile> selected = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(mediaRoot)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> addMovieIfRecognized(path, selected));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Medienscan fehlgeschlagen; Bibliothek bleibt unverändert.", exception);
        }
        return List.copyOf(selected.values());
    }

    /** Reads and validates the URL from an external movie entry. */
    @Override
    public URI readExternalUrl(String filePath) {
        Path entry = resolveEntry(filePath);
        if (!entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("Filmeintrag ist keine externe TXT-Quelle.");
        }

        try {
            return parseExternalUrl(Files.readString(entry, StandardCharsets.UTF_8).trim());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Externe Film-URL kann nicht gelesen werden: " + entry, exception);
        }
    }

    /** Resolves a stored media path while enforcing the configured root. */
    @Override
    public Path resolveMediaFile(String filePath) {
        return resolveEntry(filePath);
    }

    /** Adds a recognized movie file to the scan results. */
    private void addMovieIfRecognized(Path path, Map<String, MovieFile> selected) {
        Matcher matcher = FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }

        String title = matcher.group(1).replace('_', ' ').trim();
        int year = Integer.parseInt(matcher.group(2));
        boolean local = matcher.group(3).equalsIgnoreCase("mkv");
        Path parent = path.getParent();
        String franchise = parent.equals(mediaRoot) ? null : parent.getFileName().toString();
        String identity = (franchise == null ? "" : franchise) + "\0"
                + title.toLowerCase(Locale.ROOT) + "\0" + year;

        if (!local) {
            try {
                parseExternalUrl(Files.readString(path, StandardCharsets.UTF_8).trim());
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Externer Filmeintrag ungültig: " + path, exception);
            }
        }

        MovieFile candidate = new MovieFile(
                title, year, path.toAbsolutePath().normalize().toString(), franchise);
        MovieFile current = selected.get(identity);
        boolean currentIsExternal = current != null
                && current.filePath().toLowerCase(Locale.ROOT).endsWith(".txt");
        if (current == null || (local && currentIsExternal)) {
            selected.put(identity, candidate);
        }
    }

    /** Normalizes and validates a stored path against the media root. */
    private Path resolveEntry(String filePath) {
        Path entry = Path.of(filePath).toAbsolutePath().normalize();
        if (!entry.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Movie path lies outside configured media root.");
        }
        try {
            Path realRoot = mediaRoot.toRealPath();
            Path realEntry = entry.toRealPath();
            if (!realEntry.startsWith(realRoot) || !Files.isRegularFile(realEntry)) {
                throw new IllegalArgumentException("Movie path resolves outside configured media root.");
            }
            return realEntry;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Movie path cannot be resolved inside configured media root.",
                    exception);
        }
    }

    /** Parses an HTTP(S) URL from an external entry. */
    private static URI parseExternalUrl(String rawUrl) {
        URI uri = URI.create(rawUrl);
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("TXT enthält keine gültige HTTP(S)-URL.");
        }
        return uri;
    }
}
