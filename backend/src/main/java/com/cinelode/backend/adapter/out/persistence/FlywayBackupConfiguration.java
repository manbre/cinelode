package com.cinelode.backend.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ensures file-based SQLite databases are backed up before Flyway applies pending changes. */
@Configuration
public class FlywayBackupConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayBackupConfiguration.class);

    @Bean
    FlywayMigrationStrategy backupBeforeMigration(SqliteMigrationBackup backup) {
        return (Flyway flyway) -> {
            if (flyway.info().pending().length > 0) {
                backup.createVerifiedBackup().ifPresent(path ->
                        LOGGER.info("Verified SQLite pre-migration backup created at {}", path));
            }
            flyway.migrate();
        };
    }
}
