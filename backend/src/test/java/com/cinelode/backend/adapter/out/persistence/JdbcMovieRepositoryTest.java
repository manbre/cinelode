package com.cinelode.backend.adapter.out.persistence;

import com.cinelode.backend.domain.MetadataStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcMovieRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    /** Retries every metadata status except COMPLETE. */
    @Test
    void findsMoviesWithIncompleteMetadataForRetry() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve("metadata-status.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE movies (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    year INTEGER,
                    file_path TEXT NOT NULL UNIQUE,
                    franchise TEXT,
                    genre TEXT,
                    director TEXT,
                    actors TEXT,
                    plot TEXT,
                    runtime TEXT,
                    writer TEXT,
                    rated TEXT,
                    imdb_rating REAL,
                    oscar_wins INTEGER,
                    metadata_status TEXT
                )
                """);
        jdbc.update("INSERT INTO movies(title, file_path, metadata_status) VALUES ('Pending', '/pending', 'PENDING')");
        jdbc.update("INSERT INTO movies(title, file_path, metadata_status) VALUES ('Error', '/error', 'ERROR')");
        jdbc.update("INSERT INTO movies(title, file_path, metadata_status) VALUES ('Not found', '/not-found', 'NOT_FOUND')");
        jdbc.update("INSERT INTO movies(title, file_path, metadata_status) VALUES ('Complete', '/complete', 'COMPLETE')");
        jdbc.update("INSERT INTO movies(title, file_path) VALUES ('Missing status', '/missing-status')");

        JdbcMovieRepository repository = new JdbcMovieRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        List<MetadataStatus> statuses = repository.findMoviesWithIncompleteMetadata().stream()
                .map(movie -> movie.metadataStatus())
                .toList();

        assertEquals(Arrays.asList(
                MetadataStatus.ERROR, null, MetadataStatus.NOT_FOUND, MetadataStatus.PENDING), statuses);
    }

    /** Decodes stored HTML entities in metadata returned by the repository. */
    @Test
    void decodesHtmlEntitiesInMovieMetadata() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve("encoded-metadata.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE movies (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    year INTEGER,
                    file_path TEXT NOT NULL UNIQUE,
                    franchise TEXT,
                    genre TEXT,
                    director TEXT,
                    actors TEXT,
                    plot TEXT,
                    runtime TEXT,
                    writer TEXT,
                    rated TEXT,
                    imdb_rating REAL,
                    oscar_wins INTEGER,
                    metadata_status TEXT
                )
                """);
        jdbc.update("""
                INSERT INTO movies(title, file_path, writer, metadata_status)
                VALUES ('Black Mass', '/black-mass', 'Dick Lehr, Gerard O&apos;Neill, Jez Butterworth', 'COMPLETE')
                """);

        JdbcMovieRepository repository = new JdbcMovieRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        assertEquals("Dick Lehr, Gerard O'Neill, Jez Butterworth",
                repository.findById(1).orElseThrow().writer());
    }
}
