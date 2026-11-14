package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.adapter.out.ffmpeg.FfmpegProcessRunner;
import com.cinelode.backend.adapter.out.ffmpeg.FfprobeService;
import com.cinelode.backend.adapter.out.ffmpeg.MediaTrack;
import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.Movie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FfmpegPlaybackServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Path mediaFile;
    private Movie movie;
    private FfmpegPlaybackService playbackService;
    private FfprobeService ffprobe;
    private final List<String> command = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        mediaFile = Files.createFile(temporaryDirectory.resolve("Example (2024).mkv"));
        movie = new Movie(1L, "Example", 2024, mediaFile.toString(), null,
                null, null, null, null, null, null, null, null, null, null);

        MovieCatalog catalog = mock(MovieCatalog.class);
        when(catalog.resolveMediaFile(mediaFile.toString())).thenReturn(mediaFile);
        ffprobe = mock(FfprobeService.class);
        when(ffprobe.inspect(mediaFile)).thenReturn(List.of(
                new MediaTrack(0, "video", "h264", "und", "", true),
                new MediaTrack(1, "audio", "aac", "eng", "English", true),
                new MediaTrack(2, "audio", "aac", "deu", "Deutsch", false),
                new MediaTrack(3, "subtitle", "subrip", "eng", "English", false),
                new MediaTrack(4, "subtitle", "hdmv_pgs_subtitle", "deu", "Deutsch", false)));
        when(ffprobe.inspectDuration(mediaFile)).thenReturn(1200.0);

        FfmpegProcessRunner processRunner = mock(FfmpegProcessRunner.class);
        doAnswer(invocation -> {
            command.addAll(invocation.getArgument(0));
            return null;
        }).when(processRunner).stream(anyList(), any());
        playbackService = new FfmpegPlaybackService(catalog, ffprobe, processRunner, "ffmpeg-test");
    }

    @Test
    void previewsUseGermanAudioAndPreserveFadedShortMp4Output() throws Exception {
        var response = playbackService.play(movie, null, null, true, 0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertEquals("ffmpeg-test", command.getFirst());
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("25.000"));
        assertTrue(command.contains("0:2"));
        assertTrue(command.stream().anyMatch(value -> value.contains("fade=t=in")));
        assertTrue(command.contains("pipe:1"));
    }

    @Test
    void previewsStayEntirelyWithinTheMiddleSixtyPercent() throws Exception {
        for (double duration : List.of(7200.0, 1200.0, 60.0, 30.0, 2.0)) {
            when(ffprobe.inspectDuration(mediaFile)).thenReturn(duration);
            for (int attempt = 0; attempt < 30; attempt++) {
                command.clear();
                var response = playbackService.play(movie, null, null, true, 0);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                response.getBody().writeTo(new ByteArrayOutputStream());
                double start = Double.parseDouble(command.get(command.indexOf("-ss") + 1));
                double length = Double.parseDouble(command.get(command.indexOf("-t") + 1));
                assertTrue(start >= duration * 0.2 - 0.000001, "start outside preview range");
                assertTrue(start + length <= duration * 0.8 + 0.000001, "end outside preview range");
                assertTrue(length > 0 && length <= 25, "invalid preview length");
                assertEquals(Math.min(25, duration * 0.6), length, 0.001);
            }
        }
    }

    @Test
    void rejectsMediaTooShortForAMillisecondPreview() {
        when(ffprobe.inspectDuration(mediaFile)).thenReturn(0.001);
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT,
                playbackService.play(movie, null, null, true, 0).getStatusCode());
    }

    @Test
    void selectedAudioSubtitlesAndSeekAreEncodedAsSeparateArguments() throws Exception {
        var response = playbackService.play(movie, 1, 3, false, 121.5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertTrue(command.contains("121.500"));
        assertTrue(command.contains("0:1"));
        assertTrue(command.stream().anyMatch(value -> value.contains("si=0")));
        assertFalse(command.stream().anyMatch(value -> value.contains("121.500;")));
    }

    @Test
    void bitmapSubtitleSelectionUsesOverlayFilterAndRejectsPreviewSubtitles() throws Exception {
        var response = playbackService.play(movie, 2, 4, false, 0);
        response.getBody().writeTo(new ByteArrayOutputStream());

        assertTrue(command.contains("-filter_complex"));
        assertTrue(command.stream().anyMatch(value -> value.contains("overlay[v]")));
        assertTrue(playbackService.play(movie, null, 3, true, 0)
                .getStatusCode().is4xxClientError());
    }

    @Test
    void rejectsUnknownTracksAndInvalidSeekPositions() {
        assertEquals(HttpStatus.BAD_REQUEST,
                playbackService.play(movie, 99, null, false, 0).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                playbackService.play(movie, null, null, false, -1).getStatusCode());
    }
}
