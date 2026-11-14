package com.cinelode.backend.adapter.out.filesystem;

import com.cinelode.backend.domain.MovieFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalMovieCatalogTest {
    @TempDir
    Path mediaRoot;

    /** Confirms that a local MKV takes precedence over its matching TXT entry. */
    @Test
    void scanPrefersLocalMovieWhenExternalEntryHasSameTitleAndYear() throws Exception {
        Path movieFolder = Files.createDirectory(mediaRoot.resolve("Drama"));
        Path localMovie = Files.createFile(movieFolder.resolve("01_Example_Film (2024).mkv"));
        Files.writeString(movieFolder.resolve("Example Film (2024).txt"), "https://movies.example/watch");
        LocalMovieCatalog catalog = new LocalMovieCatalog(mediaRoot.toString());

        List<MovieFile> movies = catalog.scan();

        assertEquals(1, movies.size());
        assertEquals("Example Film", movies.getFirst().title());
        assertEquals(localMovie.toAbsolutePath().normalize().toString(), movies.getFirst().filePath());
        assertEquals("Drama", movies.getFirst().franchise());
        assertEquals(".mkv", movies.getFirst().filePath().substring(movies.getFirst().filePath().lastIndexOf('.')));
    }

    /** Confirms that a valid external URL can be read from a TXT entry. */
    @Test
    void readsValidExternalUrlFromTxtEntry() throws Exception {
        Path entry = Files.writeString(mediaRoot.resolve("Example (2024).txt"), "https://movies.example/watch");
        LocalMovieCatalog catalog = new LocalMovieCatalog(mediaRoot.toString());

        URI url = catalog.readExternalUrl(entry.toString());

        assertEquals(URI.create("https://movies.example/watch"), url);
    }

    /** Rejects stored paths that escape the configured media root. */
    @Test
    void rejectsMediaFilesOutsideConfiguredRoot() {
        LocalMovieCatalog catalog = new LocalMovieCatalog(mediaRoot.toString());

        assertThrows(IllegalArgumentException.class,
                () -> catalog.resolveMediaFile(mediaRoot.getParent().resolve("outside.mkv").toString()));
    }

    @Test
    void ignoresAndRejectsSymbolicLinksThatEscapeTheMediaRoot() throws Exception {
        Path outside = Files.createTempFile("cinelode-outside-", ".mkv");
        Path link = mediaRoot.resolve("Example (2024).mkv");
        try {
            Files.createSymbolicLink(link, outside);
            LocalMovieCatalog catalog = new LocalMovieCatalog(mediaRoot.toString());

            assertEquals(List.of(), catalog.scan());
            assertThrows(IllegalArgumentException.class, () -> catalog.resolveMediaFile(link.toString()));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }
}
