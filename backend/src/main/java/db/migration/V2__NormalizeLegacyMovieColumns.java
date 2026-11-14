package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/** Preserves movie metadata from databases created before the current column names. */
public class V2__NormalizeLegacyMovieColumns extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Set<String> columns = columns(connection, "movies");

        migrateColumn(connection, columns, "group_name", "franchise");
        columns = columns(connection, "movies");
        if (!columns.contains("runtime")) {
            execute(connection, "ALTER TABLE movies ADD COLUMN runtime TEXT");
            columns.add("runtime");
        }
        migrateColumn(connection, columns, "oscar_count", "oscar_wins");
    }

    private static void migrateColumn(
            Connection connection,
            Set<String> columns,
            String legacyColumn,
            String currentColumn) throws SQLException {
        if (columns.contains(legacyColumn) && !columns.contains(currentColumn)) {
            execute(connection, "ALTER TABLE movies RENAME COLUMN " + legacyColumn + " TO " + currentColumn);
            columns.remove(legacyColumn);
            columns.add(currentColumn);
        } else if (columns.contains(legacyColumn)) {
            execute(connection, "UPDATE movies SET " + currentColumn + "=COALESCE("
                    + currentColumn + ", " + legacyColumn + ")");
        } else if (!columns.contains(currentColumn)) {
            String type = "oscar_wins".equals(currentColumn) ? "INTEGER" : "TEXT";
            execute(connection, "ALTER TABLE movies ADD COLUMN " + currentColumn + " " + type);
            columns.add(currentColumn);
        }
    }

    private static Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
