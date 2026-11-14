package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.adapter.out.ffmpeg.FfprobeService;
import com.cinelode.backend.adapter.out.ffmpeg.MediaProbeException;
import com.cinelode.backend.adapter.out.ffmpeg.MediaTrack;
import com.cinelode.backend.application.port.SeriesCatalog;
import com.cinelode.backend.application.port.SeriesRepository;
import com.cinelode.backend.domain.SeriesEpisode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Exposes playback and track inspection for series episodes. */
@RestController
@RequestMapping("/api/series/episodes")
public class SeriesMediaController {
    private final SeriesRepository repository;
    private final SeriesCatalog catalog;
    private final FfprobeService ffprobeService;
    private final FfmpegPlaybackService ffmpegPlaybackService;

    public SeriesMediaController(
            SeriesRepository repository,
            SeriesCatalog catalog,
            FfprobeService ffprobeService,
            FfmpegPlaybackService ffmpegPlaybackService) {
        this.repository = repository;
        this.catalog = catalog;
        this.ffprobeService = ffprobeService;
        this.ffmpegPlaybackService = ffmpegPlaybackService;
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<List<MediaTrack>> tracks(@PathVariable long id) {
        Optional<Path> mediaFile = resolveEpisode(id);
        if (mediaFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(ffprobeService.inspect(mediaFile.get()));
        } catch (MediaProbeException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/duration")
    public ResponseEntity<Double> duration(@PathVariable long id) {
        Optional<Path> mediaFile = resolveEpisode(id);
        if (mediaFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(ffprobeService.inspectDuration(mediaFile.get()));
        } catch (MediaProbeException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/{id}/play", produces = "video/mp4")
    public ResponseEntity<StreamingResponseBody> play(
            @PathVariable long id,
            @RequestParam(required = false) Integer audioTrack,
            @RequestParam(required = false) Integer subtitleTrack,
            @RequestParam(defaultValue = "false") boolean preview,
            @RequestParam(defaultValue = "0") double start) {
        Optional<Path> mediaFile = resolveEpisode(id);
        if (mediaFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ffmpegPlaybackService.play(mediaFile.get(), audioTrack, subtitleTrack, preview, start);
    }

    private Optional<Path> resolveEpisode(long id) {
        Optional<SeriesEpisode> episode = repository.findEpisodeById(id);
        if (episode.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(catalog.resolveMediaFile(episode.get().filePath()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
