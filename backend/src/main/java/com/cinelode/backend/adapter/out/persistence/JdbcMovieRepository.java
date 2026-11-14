package com.cinelode.backend.adapter.out.persistence;

import com.cinelode.backend.application.port.MovieRepository;
import com.cinelode.backend.domain.MetadataStatus;
import com.cinelode.backend.domain.Movie;
import com.cinelode.backend.domain.MovieFile;
import com.cinelode.backend.domain.MovieMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Implements movie persistence with JDBC and SQLite. */
@Repository
public class JdbcMovieRepository implements MovieRepository {
    private static final RowMapper<Movie> MAPPER = (rs, rowNumber) -> new Movie(
            rs.getLong("id"),
            rs.getString("title"),
            (Integer) rs.getObject("year"),
            rs.getString("file_path"),
            rs.getString("franchise"),
            decodeMetadata(rs.getString("genre")),
            decodeMetadata(rs.getString("director")),
            decodeMetadata(rs.getString("actors")),
            decodeMetadata(rs.getString("plot")),
            decodeMetadata(rs.getString("runtime")),
            decodeMetadata(rs.getString("writer")),
            decodeMetadata(rs.getString("rated")),
            (Double) rs.getObject("imdb_rating"),
            (Integer) rs.getObject("oscar_wins"),
            rs.getString("metadata_status") == null ? null : MetadataStatus.valueOf(rs.getString("metadata_status")));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /** Creates the repository with its JDBC and transaction dependencies. */
    public JdbcMovieRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    /** Synchronizes discovered movie paths and returns removed paths. */
    @Override
    public List<String> synchronize(List<MovieFile> discoveredMovies) {
        return transactions.execute(status -> {
            Set<String> filePaths = new HashSet<>();
            for (MovieFile movie : discoveredMovies) {
                filePaths.add(movie.filePath());
                int updated = jdbc.update("""
                        UPDATE movies SET title=?, year=?, franchise=?
                        WHERE file_path=?
                        """, movie.title(), movie.year(), movie.franchise(), movie.filePath());
                if (updated == 0) {
                    jdbc.update("""
                            INSERT INTO movies(title, year, file_path, franchise, metadata_status)
                            VALUES (?, ?, ?, ?, 'PENDING')
                            """, movie.title(), movie.year(), movie.filePath(), movie.franchise());
                }
            }

            List<String> removed = filePaths.isEmpty()
                    ? jdbc.queryForList("SELECT file_path FROM movies", String.class)
                    : jdbc.queryForList("SELECT file_path FROM movies WHERE file_path NOT IN ("
                            + String.join(",", java.util.Collections.nCopies(filePaths.size(), "?")) + ")",
                            String.class, filePaths.toArray());
            if (filePaths.isEmpty()) {
                jdbc.update("DELETE FROM movies");
            } else {
                jdbc.update("DELETE FROM movies WHERE file_path NOT IN ("
                        + String.join(",", java.util.Collections.nCopies(filePaths.size(), "?")) + ")",
                        filePaths.toArray());
            }
            return removed;
        });
    }

    /** Returns movies whose metadata lookup has not completed successfully. */
    @Override
    public List<Movie> findMoviesWithIncompleteMetadata() {
        return jdbc.query("""
                SELECT * FROM movies
                WHERE metadata_status IS NULL OR metadata_status <> 'COMPLETE'
                ORDER BY title, year, file_path
                """, MAPPER);
    }

    /** Returns all movies ordered by title and year. */
    @Override
    public List<Movie> findAll() {
        return jdbc.query("SELECT * FROM movies ORDER BY title, year", MAPPER);
    }

    /** Looks up a movie by its database identifier. */
    @Override
    public Optional<Movie> findById(long id) {
        return jdbc.query("SELECT * FROM movies WHERE id=?", MAPPER, id).stream().findFirst();
    }

    /** Marks a movie whose metadata lookup returned no result. */
    @Override
    public void markMetadataNotFound(long id) {
        jdbc.update("UPDATE movies SET metadata_status='NOT_FOUND' WHERE id=?", id);
    }

    /** Marks a movie whose metadata lookup failed. */
    @Override
    public void markMetadataError(long id) {
        jdbc.update("UPDATE movies SET metadata_status='ERROR' WHERE id=?", id);
    }

    /** Stores provider metadata and marks the movie complete. */
    @Override
    public void saveMetadata(long id, MovieMetadata metadata) {
        jdbc.update("""
                UPDATE movies SET genre=?, director=?, actors=?, plot=?, runtime=?, writer=?, rated=?,
                    imdb_rating=?, oscar_wins=?, metadata_status='COMPLETE' WHERE id=?
                """, valid(metadata.genre()), valid(metadata.director()),
                valid(metadata.actors()), valid(metadata.plot()), valid(metadata.runtime()), valid(metadata.writer()),
                valid(metadata.rated()), metadata.imdbRating(), metadata.oscarWins(), id);
    }

    /** Converts missing or unavailable provider values to null. */
    private static String valid(String value) {
        return value == null || value.equals("N/A") ? null : value;
    }

    /** Decodes HTML entities from older provider metadata before exposing it to clients. */
    private static String decodeMetadata(String value) {
        return value == null ? null : HtmlUtils.htmlUnescape(value.replace("&apos;", "'"));
    }
}
