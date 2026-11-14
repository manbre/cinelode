package com.cinelode.backend.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Creates and verifies a consistent SQLite backup before pending schema migrations run. */
@Component
public class SqliteMigrationBackup {
    private final DataSource dataSource;
    private final String configuredBackupDirectory;

    public SqliteMigrationBackup(
            DataSource dataSource,
            @Value("${cinelode.database.backup-directory:}") String configuredBackupDirectory) {
        this.dataSource = dataSource;
        this.configuredBackupDirectory = configuredBackupDirectory;
    }

    public Optional<Path> createVerifiedBackup() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseFile = mainDatabaseFile(connection);
            if (databaseFile.isBlank()) {
                return Optional.empty();
            }

            Path source = Path.of(databaseFile).toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) {
                throw new IllegalStateException("SQLite database file is unavailable for backup.");
            }

            Path backupDirectory = configuredBackupDirectory.isBlank()
                    ? source.getParent().resolve("backups")
                    : Path.of(configuredBackupDirectory).toAbsolutePath().normalize();
            Files.createDirectories(backupDirectory);
            setOwnerOnlyPermissions(backupDirectory, true);

            Path backup = backupDirectory.resolve("cinelode-before-migration-"
                    + Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID() + ".sqlite");
            String escapedBackupPath = backup.toString().replace("'", "''");
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM main INTO '" + escapedBackupPath + "'");
            }

            setOwnerOnlyPermissions(backup, false);
            verifyBackup(backup);
            return Optional.of(backup);
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Could not create and verify the pre-migration SQLite backup.", exception);
        }
    }

    private static String mainDatabaseFile(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet databases = statement.executeQuery("PRAGMA database_list")) {
            while (databases.next()) {
                if ("main".equals(databases.getString("name"))) {
                    return databases.getString("file");
                }
            }
        }
        throw new IllegalStateException("SQLite main database was not found.");
    }

    private static void verifyBackup(Path backup) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup);
                Statement statement = connection.createStatement()) {
            try (ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
                if (!integrity.next() || !"ok".equalsIgnoreCase(integrity.getString(1))) {
                    throw new IllegalStateException("SQLite backup failed integrity_check.");
                }
            }
            try (ResultSet foreignKeys = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (foreignKeys.next()) {
                    throw new IllegalStateException("SQLite backup failed foreign_key_check.");
                }
            }
        }
    }

    private static void setOwnerOnlyPermissions(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            EnumSet<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            if (directory) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
            return;
        }

        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) {
            throw new IOException("The backup filesystem cannot enforce owner-only permissions.");
        }
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerAccess = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        aclView.setAcl(List.of(ownerAccess));
    }
}
