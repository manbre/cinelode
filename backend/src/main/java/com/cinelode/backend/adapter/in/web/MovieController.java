package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.application.usecase.GetMovie;
import com.cinelode.backend.application.usecase.ListMovies;
import com.cinelode.backend.domain.Movie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.cinelode.backend.application.port.MovieRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exposes movie catalog endpoints. */
@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private static final Pattern FRANCHISE_NUMBER = Pattern.compile("^(\\d+)_");

    private final ListMovies listMovies;
    private final GetMovie getMovie;
    private final MovieRepository repository;

    public MovieController(ListMovies listMovies, GetMovie getMovie, MovieRepository repository) {
        this.listMovies = listMovies;
        this.getMovie = getMovie;
        this.repository = repository;
    }

    public record PlaybackPosition(double position) {}

    @PostMapping("/{id}/progress")
    public ResponseEntity<Void> saveProgress(@PathVariable long id, @RequestBody PlaybackPosition body) {
        if (!Double.isFinite(body.position()) || body.position() < 0) return ResponseEntity.badRequest().build();
        if (getMovie.execute(id).isEmpty()) return ResponseEntity.notFound().build();
        repository.savePlaybackPosition(id, body.position());
        return ResponseEntity.noContent().build();
    }

    public record MovieDto(Long id, String title, Integer year, String franchise,
            String genre, String director, String actors, String plot, String runtime, String writer,
            String rated, Double imdbRating, Integer oscarWins, String metadataStatus,
            String franchiseNumber, String source,
            String posterUrl, String playbackUrl, double playbackPosition) {}

    @GetMapping
    public List<MovieDto> list() {
        return listMovies.execute().stream().map(MovieController::toDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieDto> get(@PathVariable long id) {
        return getMovie.execute(id)
                .map(MovieController::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static MovieDto toDto(Movie movie) {
        boolean external = isExternalEntry(movie);
        return new MovieDto(movie.id(), movie.title(), movie.year(), movie.franchise(),
                movie.genre(), movie.director(), movie.actors(), movie.plot(), movie.runtime(), movie.writer(),
                movie.rated(), movie.imdbRating(), movie.oscarWins(),
                movie.metadataStatus() == null ? null : movie.metadataStatus().name(),
                franchiseNumber(movie.filePath()),
                external ? "EXTERNAL" : "LOCAL",
                "/api/movies/" + movie.id() + "/poster",
                external ? "/api/movies/" + movie.id() + "/external-url"
                        : "/api/movies/" + movie.id() + "/stream", movie.playbackPosition());
    }

    private static String franchiseNumber(String filePath) {
        Matcher matcher = FRANCHISE_NUMBER.matcher(Path.of(filePath).getFileName().toString());
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean isExternalEntry(Movie movie) {
        return Path.of(movie.filePath()).getFileName().toString()
                .toLowerCase(Locale.ROOT).endsWith(".txt");
    }
}
