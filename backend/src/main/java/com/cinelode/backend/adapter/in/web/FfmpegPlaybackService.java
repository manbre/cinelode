
package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.adapter.out.ffmpeg.FfprobeService;
import com.cinelode.backend.adapter.out.ffmpeg.FfmpegProcessRunner;
import com.cinelode.backend.adapter.out.ffmpeg.MediaProbeException;
import com.cinelode.backend.adapter.out.ffmpeg.MediaTrack;
import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.domain.Movie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FfmpegPlaybackService {
    private final MovieCatalog catalog;
    private final FfprobeService ffprobeService;
    private final FfmpegProcessRunner processRunner;
    private final String ffmpegExecutable;

    public FfmpegPlaybackService(
            MovieCatalog catalog,
            FfprobeService ffprobeService,
            FfmpegProcessRunner processRunner,
            @Value("${cinelode.ffmpeg.path:ffmpeg}") String ffmpegExecutable) {
        this.catalog = catalog;
        this.ffprobeService = ffprobeService;
        this.processRunner = processRunner;
        this.ffmpegExecutable = ffmpegExecutable;
    }

    private static final double PREVIEW_LENGTH_SECONDS = 25.0;
    private static final double PREVIEW_RANGE_START = 0.2;
    private static final double PREVIEW_RANGE_END = 0.8;
    private static final double PREVIEW_FADE_SECONDS = 0.65;

    public ResponseEntity<StreamingResponseBody> play(
            Movie movie,
            Integer requestedAudioTrack) {
        return play(movie, requestedAudioTrack, false);
    }

    public ResponseEntity<StreamingResponseBody> play(
            Movie movie,
            Integer requestedAudioTrack,
            boolean preview) {
        return play(movie, requestedAudioTrack, preview, 0.0);
    }

    public ResponseEntity<StreamingResponseBody> play(
            Movie movie,
            Integer requestedAudioTrack,
            boolean preview,
            double startSeconds) {
        return play(movie, requestedAudioTrack, null, preview, startSeconds);
    }

    public ResponseEntity<StreamingResponseBody> play(
            Movie movie,
            Integer requestedAudioTrack,
            Integer requestedSubtitleTrack,
            boolean preview,
            double startSeconds) {
        return play(movie, requestedAudioTrack, requestedSubtitleTrack, preview, false, startSeconds);
    }

    public ResponseEntity<StreamingResponseBody> play(
            Movie movie,
            Integer requestedAudioTrack,
            Integer requestedSubtitleTrack,
            boolean preview,
            boolean widePreview,
            double startSeconds) {

        if (movie.filePath() == null
                || movie.filePath().toLowerCase(Locale.ROOT).endsWith(".txt")
                || !Double.isFinite(startSeconds)
                || startSeconds < 0.0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            return play(catalog.resolveMediaFile(movie.filePath()),
                    requestedAudioTrack, requestedSubtitleTrack, preview, widePreview, startSeconds);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<StreamingResponseBody> play(
            Path requestedMediaFile,
            Integer requestedAudioTrack,
            Integer requestedSubtitleTrack,
            boolean preview,
            double startSeconds) {
        return play(requestedMediaFile, requestedAudioTrack, requestedSubtitleTrack, preview, false, startSeconds);
    }

    public ResponseEntity<StreamingResponseBody> play(
            Path requestedMediaFile,
            Integer requestedAudioTrack,
            Integer requestedSubtitleTrack,
            boolean preview,
            boolean widePreview,
            double startSeconds) {

        if (requestedMediaFile == null || !Double.isFinite(startSeconds) || startSeconds < 0.0) {
            return ResponseEntity.badRequest().build();
        }

        final Path mediaFile;
        final List<MediaTrack> tracks;

        try {
            mediaFile = requestedMediaFile.toAbsolutePath().normalize();

            if (!Files.isRegularFile(mediaFile)
                    || !Files.isReadable(mediaFile)
                    || !mediaFile.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".mkv")) {
                return ResponseEntity.notFound().build();
            }

            tracks = ffprobeService.inspect(mediaFile);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();

        } catch (MediaProbeException exception) {
            return ResponseEntity.unprocessableContent().build();
        }

        MediaTrack videoTrack = tracks.stream()
                .filter(track -> "video".equals(track.type()))
                .findFirst()
                .orElse(null);

        if (videoTrack == null) {
            return ResponseEntity.unprocessableContent().build();
        }

        List<MediaTrack> audioTracks = tracks.stream()
                .filter(track -> "audio".equals(track.type()))
                .toList();

        MediaTrack audioTrack;

        if (requestedAudioTrack == null) {
            audioTrack = audioTracks.stream()
                    .filter(FfmpegPlaybackService::isGermanAudioTrack)
                    .findFirst()
                    .orElseGet(() -> audioTracks.stream()
                            .filter(MediaTrack::defaultTrack)
                            .findFirst()
                            .orElse(audioTracks.isEmpty() ? null : audioTracks.getFirst()));
        } else {
            audioTrack = audioTracks.stream()
                    .filter(track -> track.index() == requestedAudioTrack)
                    .findFirst()
                    .orElse(null);

            if (audioTrack == null) {
                return ResponseEntity.badRequest().build();
            }
        }

        PreviewWindow previewWindow = null;
        if (preview) {
            try {
                previewWindow = selectPreviewWindow(ffprobeService.inspectDuration(mediaFile));
            } catch (MediaProbeException exception) {
                return ResponseEntity.unprocessableContent().build();
            }
        }

        List<String> command = new ArrayList<>(List.of(
                ffmpegExecutable,
                "-hide_banner",
                "-loglevel", "error",
                "-nostdin"
        ));

        if (previewWindow != null) {
            command.addAll(List.of("-ss", formatSeconds(previewWindow.startSeconds())));
        } else if (startSeconds > 0) {
            command.addAll(List.of("-ss", formatSeconds(startSeconds)));
        }

        command.addAll(List.of("-i", mediaFile.toString()));

        if (previewWindow != null) {
            command.addAll(List.of("-t", formatSeconds(previewWindow.lengthSeconds())));
        }

        MediaTrack subtitleTrack = null;
        if (requestedSubtitleTrack != null) {
            subtitleTrack = tracks.stream()
                    .filter(track -> "subtitle".equals(track.type()))
                    .filter(track -> track.index() == requestedSubtitleTrack)
                    .findFirst()
                    .orElse(null);
            if (subtitleTrack == null || previewWindow != null) {
                return ResponseEntity.badRequest().build();
            }
        }

        if (audioTrack != null) {
            command.addAll(List.of("-map", "0:" + audioTrack.index()));
        }

        if (previewWindow != null) {
            double fadeDuration = Math.min(
                    PREVIEW_FADE_SECONDS,
                    previewWindow.lengthSeconds() / 2.0
            );
            double fadeOutStart = Math.max(
                    0.0,
                    previewWindow.lengthSeconds() - fadeDuration
            );
            String fadeInDuration = formatSeconds(fadeDuration);
            String fadeOutAt = formatSeconds(fadeOutStart);
            String videoFilter = widePreview
                    ? "crop='min(iw,ih*2.07)':'min(ih,iw/2.07)',scale=1280:618,setsar=1"
                    : "scale=w='min(1280,iw)':h=-2";
            command.addAll(List.of(
                    "-map", "0:" + videoTrack.index(),
                    "-vf", videoFilter + ",fade=t=in:st=0:d=" + fadeInDuration
                            + ",fade=t=out:st=" + fadeOutAt + ":d=" + fadeInDuration,
                    "-af", "afade=t=in:st=0:d=" + fadeInDuration
                            + ",afade=t=out:st=" + fadeOutAt + ":d=" + fadeInDuration
            ));
        } else if (subtitleTrack != null) {
            int subtitleOrdinal = 0;
            for (MediaTrack track : tracks) {
                if ("subtitle".equals(track.type())) {
                    if (track.index() == subtitleTrack.index()) {
                        break;
                    }
                    subtitleOrdinal++;
                }
            }
            if (isBitmapSubtitleTrack(subtitleTrack)) {
                command.addAll(List.of(
                        "-filter_complex",
                        "[0:" + videoTrack.index() + "][0:s:" + subtitleOrdinal + "]overlay[v]",
                        "-map", "[v]"
                ));
            } else {
                command.addAll(List.of(
                        "-map", "0:" + videoTrack.index(),
                        "-vf", "subtitles=filename='" + escapeFilterPath(mediaFile.toString())
                                + "':si=" + subtitleOrdinal
                ));
            }
        } else {
            command.addAll(List.of("-map", "0:" + videoTrack.index()));
        }

        command.addAll(List.of(
                "-map_metadata", "-1",
                "-sn",
                "-dn",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-crf", previewWindow != null ? "24" : "20",
                "-g", "48",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-frag_duration", "250000",
                "-flush_packets", "1",
                "-movflags",
                "+frag_keyframe+empty_moov+default_base_moof",
                "-f", "mp4",
                "pipe:1"
        ));

        StreamingResponseBody responseBody =
                outputStream -> processRunner.stream(command, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header("Cache-Control", "no-store, no-transform")
                .header("X-Content-Type-Options", "nosniff")
                .body(responseBody);
    }

    private static PreviewWindow selectPreviewWindow(double totalDurationSeconds) {
        double startMillis = Math.ceil(totalDurationSeconds * PREVIEW_RANGE_START * 1000);
        double endMillis = Math.floor(totalDurationSeconds * PREVIEW_RANGE_END * 1000);
        if (!Double.isFinite(endMillis) || endMillis >= Long.MAX_VALUE
                || endMillis - startMillis < 1) {
            throw new MediaProbeException("Media duration cannot provide a preview window.");
        }

        long minStart = (long) startMillis;
        long maxEnd = (long) endMillis;
        long previewLength = Math.min((long) (PREVIEW_LENGTH_SECONDS * 1000), maxEnd - minStart);
        long maxStart = maxEnd - previewLength;
        long randomStart = maxStart == minStart
                ? minStart
                : ThreadLocalRandom.current().nextLong(minStart, maxStart + 1);
        return new PreviewWindow(randomStart / 1000.0, previewLength / 1000.0);
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.US, "%.3f", Math.max(0.0, seconds));
    }

    private static String escapeFilterPath(String path) {
        return path.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace(":", "\\:")
                .replace(",", "\\,")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(";", "\\;");
    }

    private static boolean isBitmapSubtitleTrack(MediaTrack track) {
        return "dvd_subtitle".equals(track.codec())
                || "hdmv_pgs_subtitle".equals(track.codec())
                || "dvb_subtitle".equals(track.codec())
                || "xsub".equals(track.codec());
    }

    private static boolean isGermanAudioTrack(MediaTrack track) {
        String language = track.language() == null
                ? ""
                : track.language().toLowerCase(Locale.ROOT);
        String title = track.title() == null
                ? ""
                : track.title().toLowerCase(Locale.ROOT);
        return language.equals("de")
                || language.equals("deu")
                || language.equals("ger")
                || language.equals("german")
                || language.startsWith("de-")
                || title.contains("deutsch")
                || title.contains("german");
    }

    private record PreviewWindow(double startSeconds, double lengthSeconds) {}

}
