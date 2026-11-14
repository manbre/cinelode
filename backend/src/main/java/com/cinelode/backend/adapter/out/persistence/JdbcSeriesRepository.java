package com.cinelode.backend.adapter.out.persistence;

import com.cinelode.backend.application.port.SeriesRepository;
import com.cinelode.backend.domain.EpisodeFile;
import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.SeasonFile;
import com.cinelode.backend.domain.Series;
import com.cinelode.backend.domain.SeriesEpisode;
import com.cinelode.backend.domain.SeriesFile;
import com.cinelode.backend.domain.SeriesMetadata;
import com.cinelode.backend.domain.SeriesSeason;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Persists series metadata once and associates seasons and episode files. */
@Repository
public class JdbcSeriesRepository implements SeriesRepository {
    private static final RowMapper<Series> SERIES_MAPPER = JdbcSeriesRepository::mapSeries;
    private static final RowMapper<SeriesEpisode> EPISODE_MAPPER = (rs, row) -> new SeriesEpisode(
            rs.getLong("id"),
            rs.getInt("episode_number"),
            rs.getString("file_path"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcSeriesRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void synchronize(List<SeriesFile> discoveredSeries) {
        transactions.executeWithoutResult(status -> {
            Set<String> directoryPaths = discoveredSeries.stream()
                    .map(SeriesFile::directoryPath)
                    .collect(java.util.stream.Collectors.toSet());
            removeMissingSeries(directoryPaths);
            for (SeriesFile series : discoveredSeries) {
                long seriesId = upsertSeries(series);
                synchronizeSeasons(seriesId, series.seasons());
            }
        });
    }

    @Override
    public List<Series> findAll() {
        return jdbc.query("""
                SELECT s.*,
                       season.id AS season_id,
                       season.season_number,
                       season.poster_path,
                       episode.id AS episode_id,
                       episode.episode_number,
                       episode.file_path AS episode_file_path
                FROM series s
                LEFT JOIN seasons season ON season.series_id=s.id
                LEFT JOIN episodes episode ON episode.season_id=season.id
                ORDER BY s.title, s.year, season.season_number, episode.episode_number
                """, resultSet -> {
            Map<Long, SeriesAggregate> seriesById = new LinkedHashMap<>();
            int rowNumber = 0;
            while (resultSet.next()) {
                long seriesId = resultSet.getLong("id");
                SeriesAggregate series = seriesById.get(seriesId);
                if (series == null) {
                    series = new SeriesAggregate(mapSeries(resultSet, rowNumber));
                    seriesById.put(seriesId, series);
                }

                Object seasonIdValue = resultSet.getObject("season_id");
                if (seasonIdValue != null) {
                    long seasonId = ((Number) seasonIdValue).longValue();
                    SeasonAggregate season = series.seasons.get(seasonId);
                    if (season == null) {
                        season = new SeasonAggregate(
                                seasonId,
                                resultSet.getInt("season_number"),
                                resultSet.getString("poster_path"));
                        series.seasons.put(seasonId, season);
                    }
                    Object episodeIdValue = resultSet.getObject("episode_id");
                    if (episodeIdValue != null) {
                        season.episodes.add(new SeriesEpisode(
                                ((Number) episodeIdValue).longValue(),
                                resultSet.getInt("episode_number"),
                                resultSet.getString("episode_file_path")));
                    }
                }
                rowNumber++;
            }
            return seriesById.values().stream()
                    .map(SeriesAggregate::toDomain)
                    .toList();
        });
    }

    @Override
    public List<Series> findSeriesWithIncompleteMetadata() {
        return jdbc.query("""
                SELECT * FROM series
                WHERE metadata_status IS NULL OR metadata_status <> 'COMPLETE'
                ORDER BY title, year
                """, SERIES_MAPPER);
    }

    @Override
    public Optional<SeriesEpisode> findEpisodeById(long id) {
        return jdbc.query("""
                SELECT id, episode_number, file_path FROM episodes WHERE id=?
                """, EPISODE_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<String> findSeasonPosterPath(long seasonId) {
        return jdbc.query("""
                SELECT poster_path FROM seasons WHERE id=? AND poster_path IS NOT NULL
                """, (rs, row) -> rs.getString("poster_path"), seasonId).stream().findFirst();
    }

    @Override
    public void saveMetadata(long seriesId, SeriesMetadata metadata) {
        jdbc.update("""
                UPDATE series SET genre=?, actors=?, plot=?, runtime=?, writer=?, rated=?,
                    imdb_rating=?, primetime_emmy_wins=?, metadata_status='COMPLETE'
                WHERE id=?
                """, valid(metadata.genre()), valid(metadata.actors()), valid(metadata.plot()),
                valid(metadata.runtime()), valid(metadata.writer()), valid(metadata.rated()),
                metadata.imdbRating(), metadata.primetimeEmmyWins(), seriesId);
    }

    @Override
    public void markMetadataStatus(long seriesId, MetadataStatus status) {
        jdbc.update("UPDATE series SET metadata_status=? WHERE id=?", status.name(), seriesId);
    }

    private long upsertSeries(SeriesFile series) {
        Long existingId = jdbc.query("""
                SELECT id FROM series WHERE directory_path=?
                """, (rs, row) -> rs.getLong("id"), series.directoryPath()).stream().findFirst().orElse(null);
        if (existingId != null) {
            jdbc.update("""
                    UPDATE series SET title=?, year=?,
                        metadata_status=CASE
                            WHEN title<>? OR year<>? THEN 'PENDING'
                            ELSE metadata_status
                        END
                    WHERE id=?
                    """, series.title(), series.year(), series.title(), series.year(), existingId);
            return existingId;
        }

        jdbc.update("""
                INSERT INTO series(title, year, directory_path, metadata_status)
                VALUES (?, ?, ?, 'PENDING')
                """, series.title(), series.year(), series.directoryPath());
        return jdbc.queryForObject("SELECT id FROM series WHERE directory_path=?",
                Long.class, series.directoryPath());
    }

    private void synchronizeSeasons(long seriesId, List<SeasonFile> discoveredSeasons) {
        Set<Integer> seasonNumbers = new HashSet<>();
        for (SeasonFile season : discoveredSeasons) {
            seasonNumbers.add(season.seasonNumber());
            long seasonId = upsertSeason(seriesId, season);
            synchronizeEpisodes(seasonId, season.episodes());
        }

        List<SeasonIdentity> removedSeasons = jdbc.query("""
                SELECT id, season_number FROM seasons WHERE series_id=?
                """, (rs, row) -> new SeasonIdentity(rs.getLong("id"), rs.getInt("season_number")), seriesId)
                .stream()
                .filter(season -> !seasonNumbers.contains(season.seasonNumber()))
                .toList();
        for (SeasonIdentity removedSeason : removedSeasons) {
            jdbc.update("DELETE FROM episodes WHERE season_id=?", removedSeason.id());
            jdbc.update("DELETE FROM seasons WHERE id=?", removedSeason.id());
        }
    }

    private long upsertSeason(long seriesId, SeasonFile season) {
        Long existingId = jdbc.query("""
                SELECT id FROM seasons WHERE series_id=? AND season_number=?
                """, (rs, row) -> rs.getLong("id"), seriesId, season.seasonNumber())
                .stream().findFirst().orElse(null);
        if (existingId != null) {
            jdbc.update("UPDATE seasons SET poster_path=? WHERE id=?",
                    season.posterPath(), existingId);
            return existingId;
        }

        jdbc.update("""
                INSERT INTO seasons(series_id, season_number, poster_path) VALUES (?, ?, ?)
                """, seriesId, season.seasonNumber(), season.posterPath());
        return jdbc.queryForObject("""
                SELECT id FROM seasons WHERE series_id=? AND season_number=?
                """, Long.class, seriesId, season.seasonNumber());
    }

    private void synchronizeEpisodes(long seasonId, List<EpisodeFile> discoveredEpisodes) {
        Set<Integer> episodeNumbers = new HashSet<>();
        for (EpisodeFile episode : discoveredEpisodes) {
            episodeNumbers.add(episode.episodeNumber());
            int updated = jdbc.update("""
                    UPDATE episodes SET file_path=?
                    WHERE season_id=? AND episode_number=?
                    """, episode.filePath(), seasonId, episode.episodeNumber());
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO episodes(season_id, episode_number, file_path) VALUES (?, ?, ?)
                        """, seasonId, episode.episodeNumber(), episode.filePath());
            }
        }

        List<SeriesEpisode> existingEpisodes = jdbc.query("""
                SELECT id, episode_number, file_path FROM episodes WHERE season_id=?
                """, EPISODE_MAPPER, seasonId);
        for (SeriesEpisode episode : existingEpisodes) {
            if (!episodeNumbers.contains(episode.episodeNumber())) {
                jdbc.update("DELETE FROM episodes WHERE id=?", episode.id());
            }
        }
    }

    private void removeMissingSeries(Set<String> directoryPaths) {
        List<Map<String, Object>> existingSeries = jdbc.queryForList(
                "SELECT id, directory_path FROM series");
        for (Map<String, Object> series : existingSeries) {
            long id = ((Number) series.get("id")).longValue();
            if (!directoryPaths.contains(series.get("directory_path"))) {
                jdbc.update("DELETE FROM episodes WHERE season_id IN (SELECT id FROM seasons WHERE series_id=?)", id);
                jdbc.update("DELETE FROM seasons WHERE series_id=?", id);
                jdbc.update("DELETE FROM series WHERE id=?", id);
            }
        }
    }

    private static Series mapSeries(ResultSet rs, int row) throws SQLException {
        String status = rs.getString("metadata_status");
        return new Series(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getInt("year"),
                rs.getString("directory_path"),
                rs.getString("genre"),
                rs.getString("actors"),
                rs.getString("plot"),
                rs.getString("runtime"),
                rs.getString("writer"),
                rs.getString("rated"),
                numberAsDouble(rs.getObject("imdb_rating")),
                numberAsInteger(rs.getObject("primetime_emmy_wins")),
                status == null ? null : MetadataStatus.valueOf(status),
                List.of());
    }

    private static Series withSeasons(Series series, List<SeriesSeason> seasons) {
        return new Series(series.id(), series.title(), series.year(), series.directoryPath(),
                series.genre(), series.actors(), series.plot(), series.runtime(), series.writer(),
                series.rated(), series.imdbRating(), series.primetimeEmmyWins(),
                series.metadataStatus(), List.copyOf(seasons));
    }

    private static Double numberAsDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Integer numberAsInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String valid(String value) {
        return value == null || value.equalsIgnoreCase("N/A") ? null : value;
    }

    private record SeasonIdentity(long id, int seasonNumber) {}

    private static final class SeriesAggregate {
        private final Series series;
        private final Map<Long, SeasonAggregate> seasons = new LinkedHashMap<>();

        private SeriesAggregate(Series series) {
            this.series = series;
        }

        private Series toDomain() {
            List<SeriesSeason> domainSeasons = seasons.values().stream()
                    .map(SeasonAggregate::toDomain)
                    .toList();
            return withSeasons(series, domainSeasons);
        }
    }

    private static final class SeasonAggregate {
        private final long id;
        private final int seasonNumber;
        private final String posterPath;
        private final List<SeriesEpisode> episodes = new ArrayList<>();

        private SeasonAggregate(long id, int seasonNumber, String posterPath) {
            this.id = id;
            this.seasonNumber = seasonNumber;
            this.posterPath = posterPath;
        }

        private SeriesSeason toDomain() {
            return new SeriesSeason(id, seasonNumber, posterPath, List.copyOf(episodes));
        }
    }
}
