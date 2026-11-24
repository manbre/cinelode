package com.cinelode.backend.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteFlywayMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsCurrentSchemaForNewDatabase() {
        DataSource dataSource = dataSource("fresh.sqlite");

        Flyway flyway = flyway(dataSource);
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(List.of("episodes", "movies", "seasons", "series"),
                jdbc.queryForList("""
                        SELECT name FROM sqlite_master
                        WHERE type='table' AND name NOT LIKE 'sqlite_%'
                            AND name <> 'flyway_schema_history'
                        ORDER BY name
                        """, String.class));
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('movies') WHERE name='playback_position'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pragma_table_info('episodes') WHERE name='playback_position'", Integer.class));
        assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
        assertEquals(0, jdbc.queryForList("PRAGMA foreign_key_check").size());
    }

    @Test
    void createsAndVerifiesConsistentBackupBeforeMigration() throws Exception {
        Path source = temporaryDirectory.resolve("source.sqlite");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + source);
        Path backupDirectory = temporaryDirectory.resolve("private-backups");
        Optional<Path> backup;
        try (Connection writer = dataSource.getConnection();
                Statement statement = writer.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE library_items (id INTEGER PRIMARY KEY, title TEXT NOT NULL)");
            statement.execute("INSERT INTO library_items(id, title) VALUES (1, 'Preserved')");
            assertTrue(Files.exists(Path.of(source + "-wal")));
            assertTrue(Files.exists(Path.of(source + "-shm")));
            backup = new SqliteMigrationBackup(dataSource, backupDirectory.toString())
                    .createVerifiedBackup();
        }

        assertTrue(backup.isPresent());
        assertTrue(backup.get().startsWith(backupDirectory));
        assertFalse(backup.get().equals(source));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup.get());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT title FROM library_items WHERE id=1")) {
            assertTrue(resultSet.next());
            assertEquals("Preserved", resultSet.getString(1));
            assertEquals("ok", queryString(statement, "PRAGMA integrity_check"));
            assertFalse(hasRows(statement, "PRAGMA foreign_key_check"));
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
                Statement statement = connection.createStatement()) {
            assertEquals(1, queryInt(statement, "SELECT COUNT(*) FROM library_items"));
        }
    }

    @Test
    void skipsBackupForInMemoryDatabase() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");

        assertTrue(new SqliteMigrationBackup(dataSource, "").createVerifiedBackup().isEmpty());
    }

    @Test
    @EnabledIfSystemProperty(named = "cinelode.migration.rehearsal.database", matches = ".+")
    void rehearsesConfiguredDatabaseCopy() throws Exception {
        Path database = Path.of(System.getProperty("cinelode.migration.rehearsal.database"));
        Path backupDirectory = Path.of(System.getProperty(
                "cinelode.migration.rehearsal.backup-directory",
                database.getParent().resolve("backups").toString()));
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + database);

        Flyway flyway = flyway(dataSource);
        new FlywayBackupConfiguration()
                .backupBeforeMigration(new SqliteMigrationBackup(dataSource, backupDirectory.toString()))
                .migrate(flyway);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
        assertEquals(0, jdbc.queryForList("PRAGMA foreign_key_check").size());
        try (var backups = Files.list(backupDirectory)) {
            assertTrue(backups.anyMatch(path ->
                    path.getFileName().toString().startsWith("cinelode-before-migration-")));
        }
    }

    private DataSource dataSource(String fileName) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temporaryDirectory.resolve(fileName));
        return dataSource;
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    private static String queryString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static int queryInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static boolean hasRows(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next();
        }
    }
}
