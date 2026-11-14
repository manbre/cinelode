package com.cinelode.backend.adapter.out.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalPosterStorageTest {
    @TempDir
    Path mediaRoot;

    @Test
    void doesNotReturnOrOverwriteAPosterSymlinkThatEscapesTheMediaRoot() throws Exception {
        Path movie = Files.createFile(mediaRoot.resolve("Example (2024).mkv"));
        Path outsidePoster = Files.createTempFile("cinelode-outside-", ".jpg");
        Files.writeString(outsidePoster, "keep outside");
        Path poster = mediaRoot.resolve("Example (2024).jpg");
        try {
            Files.createSymbolicLink(poster, outsidePoster);
            LocalPosterStorage storage = new LocalPosterStorage(mediaRoot.toString());

            assertEquals(java.util.Optional.empty(), storage.findPoster(movie.toString()));
            assertThrows(IllegalArgumentException.class, () -> storage.downloadIfMissing(
                    movie.toString(), "https://m.media-amazon.com/poster.jpg"));
            assertEquals("keep outside", Files.readString(outsidePoster));
        } finally {
            Files.deleteIfExists(poster);
            Files.deleteIfExists(outsidePoster);
        }
    }

    @Test
    void boundedReadAcceptsSmallBodiesAndRejectsOversizedBodies() throws Exception {
        byte[] content = {1, 2, 3, 4};

        assertArrayEquals(content, LocalPosterStorage.readBounded(new ByteArrayInputStream(content), 4));
        assertThrows(IOException.class, () -> LocalPosterStorage.readBounded(
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}),
                4));
    }

    @Test
    void deletesOrphanedPosterAfterItsMediaFileWasRemoved() throws Exception {
        Path movie = Files.createFile(mediaRoot.resolve("Example (2024).mkv"));
        Path poster = Files.createFile(mediaRoot.resolve("Example (2024).jpg"));
        Files.delete(movie);
        LocalPosterStorage storage = new LocalPosterStorage(mediaRoot.toString());

        storage.deleteOrphanedPosters(List.of(), List.of(movie.toString()));

        assertFalse(Files.exists(poster));
    }
}
