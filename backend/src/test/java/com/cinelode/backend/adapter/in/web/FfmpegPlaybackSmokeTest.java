package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.adapter.out.ffmpeg.FfmpegProcessRunner;
import com.cinelode.backend.adapter.out.ffmpeg.FfprobeService;
import com.cinelode.backend.application.port.MovieCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "CINELODE_FFMPEG_SMOKE", matches = "true")
class FfmpegPlaybackSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void transcodesRealMkvMediaIntoReadableFragmentedMp4() throws Exception {
        Path input = temporaryDirectory.resolve("smoke.mkv");
        Path output = temporaryDirectory.resolve("playback.mp4");
        Path generationLog = temporaryDirectory.resolve("generation.log");
        Path probeOutput = temporaryDirectory.resolve("format.txt");
        Path probeLog = temporaryDirectory.resolve("probe.log");

        run(List.of("ffmpeg", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=1000",
                "-t", "2", "-c:v", "mpeg4", "-c:a", "aac", "-shortest", "-y",
                input.toString()), temporaryDirectory.resolve("generation.out"), generationLog);

        MovieCatalog catalog = mock(MovieCatalog.class);
        FfprobeService probe = new FfprobeService(catalog, "ffprobe");
        assertEquals(2.0, probe.inspectDuration(input), 0.2);
        assertTrue(probe.inspect(input).stream().anyMatch(track -> "video".equals(track.type())));
        FfmpegPlaybackService playback = new FfmpegPlaybackService(
                catalog, probe, new FfmpegProcessRunner(), "ffmpeg");
        var response = playback.play(input, null, null, false, 0);
        assertEquals(200, response.getStatusCode().value());
        try (OutputStream stream = Files.newOutputStream(output)) {
            StreamingResponseBody body = response.getBody();
            body.writeTo(stream);
        }

        assertTrue(Files.size(output) > 0);
        run(List.of("ffprobe", "-v", "error", "-show_entries", "format=format_name",
                "-of", "default=noprint_wrappers=1:nokey=1", output.toString()), probeOutput, probeLog);
        assertTrue(Files.readString(probeOutput).contains("mp4"));
    }

    private void run(List<String> command, Path stdout, Path errorLog) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectError(errorLog.toFile())
                .redirectOutput(stdout.toFile())
                .start();
        if (!process.waitFor(Duration.ofSeconds(30).toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IllegalStateException("Media smoke command timed out: " + command.getFirst());
        }
        assertEquals(0, process.exitValue(), Files.readString(errorLog));
    }
}
