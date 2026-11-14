package com.cinelode.backend.adapter.out.ffmpeg;

import com.cinelode.backend.application.port.MovieCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FfprobeServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesSupportedTracksFromFfprobeJson() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("Example (2024).mkv"));
        Path executable = createExecutable("""
                printf '%s' '{"streams":[
                  {"index":0,"codec_type":"video","codec_name":"h264","disposition":{"default":1}},
                  {"index":1,"codec_type":"audio","codec_name":"aac","tags":{"language":"deu","title":"Deutsch"},"disposition":{"default":0}},
                  {"index":2,"codec_type":"data","codec_name":"bin_data"}
                ]}'
                """);
        FfprobeService service = new FfprobeService(mock(MovieCatalog.class), executable.toString());

        List<MediaTrack> tracks = service.inspect(media);

        assertEquals(List.of(
                new MediaTrack(0, "video", "h264", "und", "", true),
                new MediaTrack(1, "audio", "aac", "deu", "Deutsch", false)), tracks);
    }

    @Test
    void acceptsOnlyFinitePositiveMediaDurations() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("Duration (2024).mkv"));
        Path validExecutable = createExecutable("printf '%s' '{\"format\":{\"duration\":\"123.5\"}}'");
        assertEquals(123.5, new FfprobeService(mock(MovieCatalog.class), validExecutable.toString())
                .inspectDuration(media));

        for (String duration : List.of("NaN", "Infinity", "0", "-1", "N/A", "")) {
            Path invalidExecutable = createExecutable("printf '%s' '{\"format\":{\"duration\":\""
                    + duration + "\"}}'");
            FfprobeService service = new FfprobeService(mock(MovieCatalog.class), invalidExecutable.toString());
            assertThrows(MediaProbeException.class, () -> service.inspectDuration(media));
        }
    }

    @Test
    void sharesProbeForTracksAndDurationAndInvalidatesChangedFiles() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("cached.mkv"));
        Path counter = temporaryDirectory.resolve("counter");
        Path executable = createExecutable("printf 'x' >> '" + counter + "'\n"
                + "printf '%s' '{\"streams\":[{\"index\":0,\"codec_type\":\"video\"}],"
                + "\"format\":{\"duration\":\"123.5\"}}'");
        FfprobeService service = new FfprobeService(mock(MovieCatalog.class), executable.toString());

        assertEquals(1, service.inspect(media).size());
        assertEquals(123.5, service.inspectDuration(media));
        assertEquals(123.5, service.inspectDuration(media));
        assertEquals("x", Files.readString(counter));

        Files.writeString(media, "changed");
        assertEquals(123.5, service.inspectDuration(media));
        assertEquals(1, service.inspect(media).size());
        assertEquals("xx", Files.readString(counter));
    }

    @Test
    void concurrentRequestsShareOneProbe() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("concurrent.mkv"));
        Path counter = temporaryDirectory.resolve("concurrent-counter");
        Path executable = createExecutable("printf 'x' >> '" + counter + "'\n"
                + "sleep 0.3\n"
                + "printf '%s' '{\"streams\":[{\"index\":0,\"codec_type\":\"video\"}],"
                + "\"format\":{\"duration\":\"42\"}}'");
        FfprobeService service = new FfprobeService(mock(MovieCatalog.class), executable.toString());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tracks = executor.submit(() -> {
                start.await();
                return service.inspect(media);
            });
            var duration = executor.submit(() -> {
                start.await();
                return service.inspectDuration(media);
            });
            start.countDown();
            assertEquals(1, tracks.get(5, TimeUnit.SECONDS).size());
            assertEquals(42.0, duration.get(5, TimeUnit.SECONDS));
        }
        assertEquals("x", Files.readString(counter));
    }

    @Test
    void missingDurationDoesNotPreventTrackInspection() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("no-duration.mkv"));
        Path executable = createExecutable("printf '%s' '{\"streams\":[{\"index\":0,\"codec_type\":\"video\"}]}'");
        FfprobeService service = new FfprobeService(mock(MovieCatalog.class), executable.toString());
        assertEquals(1, service.inspect(media).size());
        assertThrows(MediaProbeException.class, () -> service.inspectDuration(media));
    }

    @Test
    void failedProbeIsNotCached() throws Exception {
        Path media = Files.createFile(temporaryDirectory.resolve("retry.mkv"));
        Path counter = temporaryDirectory.resolve("fail-counter");
        Path executable = createExecutable("printf 'x' >> '" + counter + "'\nexit 1");
        FfprobeService service = new FfprobeService(mock(MovieCatalog.class), executable.toString());
        assertThrows(MediaProbeException.class, () -> service.inspect(media));
        assertThrows(MediaProbeException.class, () -> service.inspect(media));
        assertEquals("xx", Files.readString(counter));
    }

    private Path createExecutable(String body) throws IOException {
        Path executable = Files.createTempFile(temporaryDirectory, "fake-ffprobe-", ".sh");
        Files.writeString(executable, "#!/bin/sh\n" + body + "\n");
        if (!executable.toFile().setExecutable(true)) {
            throw new IOException("Could not make test FFprobe executable.");
        }
        return executable;
    }
}
