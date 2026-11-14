package com.cinelode.backend.adapter.out.filesystem;

import com.cinelode.backend.domain.SeriesFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalSeriesCatalogTest {
    @TempDir
    Path seriesRoot;

    @Test
    void parsesSeriesFolderEpisodeNumbersAndSeasonPosters() throws Exception {
        Path show = Files.createDirectory(seriesRoot.resolve("Breaking Bad (2008)"));
        Files.createFile(show.resolve("BB_01_01.mkv"));
        Files.createFile(show.resolve("BB_01_03.mkv"));
        Files.createFile(show.resolve("BB_02_01.mkv"));
        Path firstSeasonPoster = Files.createFile(show.resolve("BB_01.jpg"));
        Files.createFile(show.resolve("ignore.mkv"));
        Files.createFile(show.resolve("BB_01_02.txt"));
        LocalSeriesCatalog catalog = new LocalSeriesCatalog(seriesRoot.toString());

        SeriesFile series = catalog.scan().getFirst();

        assertEquals("Breaking Bad", series.title());
        assertEquals(2008, series.year());
        assertEquals(2, series.seasons().size());
        assertEquals(1, series.seasons().getFirst().seasonNumber());
        assertEquals(firstSeasonPoster.toAbsolutePath().normalize().toString(),
                series.seasons().getFirst().posterPath());
        assertEquals(java.util.List.of(1, 3), series.seasons().getFirst().episodes().stream()
                .map(episode -> episode.episodeNumber()).toList());
        assertEquals(2, series.seasons().get(1).seasonNumber());
        assertEquals(1, series.seasons().get(1).episodes().getFirst().episodeNumber());
    }

    @Test
    void rejectsMediaPathsOutsideTheConfiguredRoot() {
        LocalSeriesCatalog catalog = new LocalSeriesCatalog(seriesRoot.toString());

        assertThrows(IllegalArgumentException.class,
                () -> catalog.resolveMediaFile(seriesRoot.getParent().resolve("outside.mkv").toString()));
    }

    @Test
    void rejectsDuplicateEpisodeNumbersWithinASeason() throws Exception {
        Path show = Files.createDirectory(seriesRoot.resolve("Example (2020)"));
        Files.createFile(show.resolve("AA_01_01.mkv"));
        Files.createFile(show.resolve("BB_01_01.mkv"));
        LocalSeriesCatalog catalog = new LocalSeriesCatalog(seriesRoot.toString());

        assertThrows(IllegalStateException.class, catalog::scan);
    }

    @Test
    void ignoresAndRejectsSymbolicLinksThatEscapeTheSeriesRoot() throws Exception {
        Path outside = Files.createTempFile("cinelode-outside-", ".mkv");
        Path show = Files.createDirectory(seriesRoot.resolve("Example (2020)"));
        Path link = show.resolve("AA_01_01.mkv");
        try {
            Files.createSymbolicLink(link, outside);
            LocalSeriesCatalog catalog = new LocalSeriesCatalog(seriesRoot.toString());

            assertEquals(java.util.List.of(), catalog.scan());
            assertThrows(IllegalArgumentException.class, () -> catalog.resolveMediaFile(link.toString()));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }
}
