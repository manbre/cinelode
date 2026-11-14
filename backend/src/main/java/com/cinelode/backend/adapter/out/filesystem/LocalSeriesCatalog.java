package com.cinelode.backend.adapter.out.filesystem;

import com.cinelode.backend.application.port.SeriesCatalog;
import com.cinelode.backend.domain.EpisodeFile;
import com.cinelode.backend.domain.SeasonFile;
import com.cinelode.backend.domain.SeriesFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Discovers series directories, season posters, and episode files. */
@Component
public class LocalSeriesCatalog implements SeriesCatalog {
    private static final Pattern SERIES_DIRECTORY =
            Pattern.compile("^(.+) \\((\\d{4})\\)$");
    private static final Pattern EPISODE_FILE =
            Pattern.compile("^.+_(\\d{2})_(\\d{2})\\.mkv$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEASON_POSTER =
            Pattern.compile("^.+_(\\d{2})\\.jpg$", Pattern.CASE_INSENSITIVE);

    private final Path seriesRoot;

    public LocalSeriesCatalog(@Value("${cinelode.series.root}") String seriesRoot) {
        this.seriesRoot = Path.of(seriesRoot).toAbsolutePath().normalize();
    }

    @Override
    public List<SeriesFile> scan() {
        if (!Files.isDirectory(seriesRoot) || !Files.isReadable(seriesRoot)) {
            throw new IllegalStateException("Serienordner fehlt oder ist nicht lesbar: " + seriesRoot);
        }

        try (Stream<Path> entries = Files.list(seriesRoot)) {
            return entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(this::scanSeriesDirectory)
                    .flatMap(java.util.Optional::stream)
                    .toList();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Serienscan fehlgeschlagen; Bibliothek bleibt unverändert.", exception);
        }
    }

    @Override
    public Path resolveMediaFile(String filePath) {
        Path mediaFile = Path.of(filePath).toAbsolutePath().normalize();
        if (!mediaFile.startsWith(seriesRoot)) {
            throw new IllegalArgumentException("Series media path lies outside configured series root.");
        }
        try {
            Path realRoot = seriesRoot.toRealPath();
            Path realMediaFile = mediaFile.toRealPath();
            if (!realMediaFile.startsWith(realRoot) || !Files.isRegularFile(realMediaFile)) {
                throw new IllegalArgumentException("Series media path resolves outside configured series root.");
            }
            return realMediaFile;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Series media path cannot be resolved inside configured root.",
                    exception);
        }
    }

    private java.util.Optional<SeriesFile> scanSeriesDirectory(Path directory) {
        Matcher seriesMatcher = SERIES_DIRECTORY.matcher(directory.getFileName().toString());
        if (!seriesMatcher.matches()) {
            return java.util.Optional.empty();
        }

        String title = seriesMatcher.group(1).trim();
        int year = Integer.parseInt(seriesMatcher.group(2));
        Map<Integer, Map<Integer, Path>> episodesBySeason = new TreeMap<>();
        Map<Integer, Path> postersBySeason = new TreeMap<>();

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                String filename = file.getFileName().toString();
                Matcher episodeMatcher = EPISODE_FILE.matcher(filename);
                if (episodeMatcher.matches()) {
                    int seasonNumber = Integer.parseInt(episodeMatcher.group(1));
                    int episodeNumber = Integer.parseInt(episodeMatcher.group(2));
                    Path previous = episodesBySeason
                            .computeIfAbsent(seasonNumber, ignored -> new TreeMap<>())
                            .putIfAbsent(episodeNumber, file);
                    if (previous != null) {
                        throw new IllegalStateException("Doppelte Episodennummer " + seasonNumber + "x"
                                + episodeNumber + " in " + directory);
                    }
                    continue;
                }

                Matcher posterMatcher = SEASON_POSTER.matcher(filename);
                if (posterMatcher.matches()) {
                    int seasonNumber = Integer.parseInt(posterMatcher.group(1));
                    Path previous = postersBySeason.putIfAbsent(seasonNumber, file);
                    if (previous != null) {
                        throw new IllegalStateException("Mehrere Staffelposter für Staffel "
                                + seasonNumber + " in " + directory);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Serienordner kann nicht gelesen werden: " + directory, exception);
        }

        List<SeasonFile> seasons = new ArrayList<>();
        episodesBySeason.forEach((seasonNumber, seasonEpisodes) -> {
            List<EpisodeFile> episodes = seasonEpisodes.entrySet().stream()
                    .map(entry -> new EpisodeFile(
                            entry.getKey(),
                            entry.getValue().toAbsolutePath().normalize().toString()))
                    .toList();
            Path poster = postersBySeason.get(seasonNumber);
            seasons.add(new SeasonFile(seasonNumber,
                    poster == null ? null : poster.toAbsolutePath().normalize().toString(),
                    episodes));
        });

        if (seasons.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new SeriesFile(
                title, year, directory.toAbsolutePath().normalize().toString(), List.copyOf(seasons)));
    }
}
