
package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.adapter.out.ffmpeg.FfprobeService;
import com.cinelode.backend.adapter.out.ffmpeg.MediaProbeException;
import com.cinelode.backend.adapter.out.ffmpeg.MediaTrack;
import com.cinelode.backend.application.port.MovieCatalog;
import com.cinelode.backend.application.port.PosterStorage;
import com.cinelode.backend.application.usecase.GetMovie;
import com.cinelode.backend.domain.Movie;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Exposes poster, external playback, track inspection, and local media streaming endpoints. */
@RestController
@RequestMapping("/api/movies")
public class MovieMediaController {

    private final GetMovie getMovie;
    private final MovieCatalog catalog;
    private final PosterStorage posters;
    private final MovieStreamService movieStreamService;
    private final FfprobeService ffprobeService;
    private final FfmpegPlaybackService ffmpegPlaybackService;

    public MovieMediaController(
            GetMovie getMovie,
            MovieCatalog catalog,
            PosterStorage posters,
            MovieStreamService movieStreamService,
            FfprobeService ffprobeService,
            FfmpegPlaybackService ffmpegPlaybackService) {

        this.getMovie = getMovie;
        this.catalog = catalog;
        this.posters = posters;
        this.movieStreamService = movieStreamService;
        this.ffprobeService = ffprobeService;
        this.ffmpegPlaybackService = ffmpegPlaybackService;
    }

    public record ExternalPlayback(String url) {}

    @GetMapping("/{id}/external-url")
    public ResponseEntity<ExternalPlayback> externalUrl(
            @PathVariable long id) {

        Optional<Movie> result = getMovie.execute(id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Movie movie = result.get();

        if (!isExternalEntry(movie)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            URI uri = catalog.readExternalUrl(movie.filePath());

            return ResponseEntity.ok(
                    new ExternalPlayback(uri.toASCIIString())
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.unprocessableContent().build();
        }
    }

    @GetMapping("/{id}/poster")
    public ResponseEntity<Resource> poster(
            @PathVariable long id) {

        Optional<Movie> result = getMovie.execute(id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Path> poster = posters.findPoster(
                result.get().filePath()
        );

        return poster.<ResponseEntity<Resource>>map(path ->
                        ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_JPEG)
                                .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<List<MediaTrack>> tracks(
            @PathVariable long id) {

        Optional<Movie> result = getMovie.execute(id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (isExternalEntry(result.get())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            return ResponseEntity.ok(
                    ffprobeService.inspect(result.get())
            );

        } catch (MediaProbeException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/duration")
    public ResponseEntity<Double> duration(@PathVariable long id) {
        Optional<Movie> result = getMovie.execute(id);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (isExternalEntry(result.get())) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(ffprobeService.inspectDuration(result.get()));
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
            @RequestParam(defaultValue = "false") boolean wide,
            @RequestParam(defaultValue = "0") double start) {

        Optional<Movie> result = getMovie.execute(id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ffmpegPlaybackService.play(
                result.get(),
                audioTrack,
                subtitleTrack,
                preview,
                wide,
                start
        );
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> stream(
            @PathVariable long id,
            @RequestHeader HttpHeaders headers) {

        Optional<Movie> result = getMovie.execute(id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return movieStreamService.stream(
                result.get(),
                headers
        );
    }

    private static boolean isExternalEntry(Movie movie) {
        return movie.filePath()
                .toLowerCase(Locale.ROOT)
                .endsWith(".txt");
    }
}
