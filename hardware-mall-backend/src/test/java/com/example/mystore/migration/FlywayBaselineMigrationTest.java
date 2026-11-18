package com.example.mystore.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
class FlywayBaselineMigrationTest {

    private static final Path MIGRATIONS = Path.of("..", "db", "migration").toAbsolutePath();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("flyway_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void baseline_appliesOnEmptySchemaAndIsRepeatable() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + MIGRATIONS)
                .load();

        MigrateResult first = flyway.migrate();
        assertThat(first.migrationsExecuted).isEqualTo(1);

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();

        assertThat(tableExists("flyway_test", "level_category_rate")).isTrue();
        assertThat(tableExists("flyway_test", "order_idempotency")).isTrue();
        assertThat(tableExists("flyway_test", "payment_record")).isTrue();
        assertThat(columnExists("flyway_test", "order_item", "pricing_rate")).isTrue();
        assertThat(query("flyway_test",
                "SELECT COUNT(*) FROM user_level WHERE id = 1 AND is_retail = 1"))
                .containsExactly("1");
    }

    @Test
    void arbitraryV2_appliesWithoutAnyPricingFingerprint() throws IOException, SQLException {
        execute("CREATE DATABASE probe");
        Path probeDir = Files.createTempDirectory("flyway-probe");
        Files.copy(MIGRATIONS.resolve("V1__baseline.sql"), probeDir.resolve("V1__baseline.sql"));
        Files.writeString(probeDir.resolve("V2__probe.sql"),
                "ALTER TABLE `order_item` ADD COLUMN `deploy_probe` TINYINT NULL;\n");

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl().replace("/flyway_test", "/probe"),
                        MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + probeDir)
                .load()
                .migrate();

        assertThat(columnExists("probe", "order_item", "deploy_probe")).isTrue();
    }

    private static boolean tableExists(String schema, String table) {
        return !query(schema,
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "'")
                .isEmpty();
    }

    private static boolean columnExists(String schema, String table, String column) {
        return !query(schema,
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "' " +
                        "AND column_name = '" + column + "'")
                .isEmpty();
    }

    private static List<String> query(String schema, String sql) {
        List<String> values = new ArrayList<>();
        try (Connection connection = open(schema);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
        return values;
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = open(null); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection open(String schema) throws SQLException {
        String database = schema == null ? "" : schema;
        return DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database +
                        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                "root",
                MYSQL.getPassword());
    }
}
