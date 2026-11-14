package com.cinelode.backend.adapter.out.persistence;

import com.cinelode.backend.domain.EpisodeFile;
import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.SeasonFile;
import com.cinelode.backend.domain.SeriesFile;
import com.cinelode.backend.domain.SeriesMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JdbcSeriesRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesMetadataOnceAndSynchronizesSeasonEpisodeAssociations() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve("series.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE series (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL, year INTEGER NOT NULL, directory_path TEXT NOT NULL UNIQUE,
                    genre TEXT, actors TEXT, plot TEXT, runtime TEXT, writer TEXT, rated TEXT,
                    imdb_rating REAL, primetime_emmy_wins INTEGER, metadata_status TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE seasons (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    series_id INTEGER NOT NULL, season_number INTEGER NOT NULL, poster_path TEXT,
                    UNIQUE(series_id, season_number)
                )
                """);
        jdbc.execute("""
                CREATE TABLE episodes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    season_id INTEGER NOT NULL, episode_number INTEGER NOT NULL, file_path TEXT NOT NULL UNIQUE,
                    UNIQUE(season_id, episode_number)
                )
                """);
        JdbcSeriesRepository repository = new JdbcSeriesRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        SeriesFile initial = new SeriesFile("Breaking Bad", 2008, "/shows/Breaking Bad (2008)", List.of(
                new SeasonFile(1, "/shows/Breaking Bad (2008)/BB_01.jpg", List.of(
                        new EpisodeFile(1, "/shows/Breaking Bad (2008)/BB_01_01.mkv"),
                        new EpisodeFile(3, "/shows/Breaking Bad (2008)/BB_01_03.mkv"))),
                new SeasonFile(2, null, List.of(
                        new EpisodeFile(1, "/shows/Breaking Bad (2008)/BB_02_01.mkv")))));
        repository.synchronize(List.of(initial));

        long seriesId = jdbc.queryForObject("SELECT id FROM series", Long.class);
        repository.saveMetadata(seriesId,
                new SeriesMetadata("Crime", "Actors", "Plot", "45 min", "Writer", "TV-MA", 9.5, 16));
        var initialSeries = repository.findAll().getFirst();
        assertEquals(2, initialSeries.seasons().size());
        assertEquals(List.of(1, 3), initialSeries.seasons().getFirst().episodes().stream()
                .map(episode -> episode.episodeNumber()).toList());

        repository.synchronize(List.of(new SeriesFile("Breaking Bad", 2008, initial.directoryPath(), List.of(
                new SeasonFile(1, initial.seasons().getFirst().posterPath(), List.of(
                        new EpisodeFile(1, "/shows/Breaking Bad (2008)/BB_01_01.mkv")))))));

        var storedSeries = repository.findAll().getFirst();

        assertEquals(1, storedSeries.seasons().size());
        assertEquals("Crime", storedSeries.genre());
        assertEquals(16, storedSeries.primetimeEmmyWins());
        assertEquals(MetadataStatus.COMPLETE, storedSeries.metadataStatus());
        assertEquals(List.of(1), storedSeries.seasons().getFirst().episodes().stream()
                .map(episode -> episode.episodeNumber()).toList());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM series", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM episodes", Integer.class));
        assertNull(repository.findSeasonPosterPath(999L).orElse(null));

        repository.synchronize(List.of());
        assertEquals(0, repository.findAll().size());
    }
}
