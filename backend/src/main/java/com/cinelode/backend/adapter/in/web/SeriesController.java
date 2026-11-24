package com.cinelode.backend.adapter.in.web;

import com.cinelode.backend.application.port.SeriesCatalog;
import com.cinelode.backend.application.port.SeriesRepository;
import com.cinelode.backend.domain.Series;
import com.cinelode.backend.domain.SeriesSeason;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Exposes series seasons as catalog cards and serves their local posters. */
@RestController
@RequestMapping("/api/series")
public class SeriesController {
    private final SeriesRepository repository;
    private final SeriesCatalog catalog;

    public SeriesController(SeriesRepository repository, SeriesCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    public record EpisodeDto(long id, int episodeNumber, double playbackPosition) {}

    public record SeasonDto(
            long id,
            String title,
            Integer year,
            int seasonNumber,
            String genre,
            String actors,
            String plot,
            String runtime,
            String writer,
            String rated,
            Double imdbRating,
            Integer primetimeEmmyWins,
            String metadataStatus,
            String posterUrl,
            List<EpisodeDto> episodes) {}

    @GetMapping
    public List<SeasonDto> listSeasons() {
        return repository.findAll().stream()
                .flatMap(series -> series.seasons().stream().map(season -> toDto(series, season)))
                .toList();
    }

    @GetMapping("/seasons/{seasonId}/poster")
    public ResponseEntity<Resource> poster(@PathVariable long seasonId) {
        Optional<String> posterPath = repository.findSeasonPosterPath(seasonId);
        if (posterPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path poster = catalog.resolveMediaFile(posterPath.get());
            if (!Files.isRegularFile(poster) || !Files.isReadable(poster)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(new FileSystemResource(poster));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private static SeasonDto toDto(Series series, SeriesSeason season) {
        List<EpisodeDto> episodes = season.episodes().stream()
                .map(episode -> new EpisodeDto(episode.id(), episode.episodeNumber(), episode.playbackPosition()))
                .toList();
        return new SeasonDto(
                season.id(),
                series.title(),
                series.year(),
                season.seasonNumber(),
                series.genre(),
                series.actors(),
                series.plot(),
                series.runtime(),
                series.writer(),
                series.rated(),
                series.imdbRating(),
                series.primetimeEmmyWins(),
                series.metadataStatus() == null ? null : series.metadataStatus().name(),
                season.posterPath() == null ? null : "/api/series/seasons/" + season.id() + "/poster",
                episodes);
    }
}
