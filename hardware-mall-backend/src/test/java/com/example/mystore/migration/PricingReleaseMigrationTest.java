package com.example.mystore.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
class PricingReleaseMigrationTest {

    private static final String DATABASE = "hardware_mall";
    private static final String SNAPSHOT_RESOURCE = "db/production-original-schema.sql";
    private static final String BASE_MIGRATION_RESOURCE =
            "db/migration_2026-11-02_multi_level_pricing_release.sql";
    private static final String RATE_MIGRATION_RESOURCE =
            "db/migration_2026-11-09_level_category_rate.sql";
    private static final String INIT_RESOURCE = "db/init.sql";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("release-test");

    @BeforeAll
    static void copyScriptsToContainer() throws IOException {
        copyResource(SNAPSHOT_RESOURCE, "/tmp/production-original-schema.sql");
        copyResource(BASE_MIGRATION_RESOURCE, "/tmp/pricing-release.sql");
        copyResource(RATE_MIGRATION_RESOURCE, "/tmp/pricing-rate-rename.sql");
        copyResource(INIT_RESOURCE, "/tmp/init.sql");
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        mysql("-e", "DROP DATABASE IF EXISTS `" + DATABASE + "`;");
    }

    @Test
    void migration_upgradesProductionSchemaToTheFinalPricingModel() throws Exception {
        loadSnapshot(DATABASE);

        runMigrations(DATABASE);
        assertFinalPricingModel(DATABASE);
        assertThat(queryStrings(DATABASE,
                "SELECT CONCAT(name, ':', is_retail, ':', status) FROM user_level ORDER BY id"))
                .containsExactly("零售:1:1");
        assertThat(queryStrings(DATABASE, "SELECT CAST(COUNT(*) AS CHAR) FROM level_category_rate"))
                .containsExactly("0");
        assertThat(queryStrings(DATABASE, "SELECT CAST(COUNT(*) AS CHAR) FROM user WHERE level_id = 1"))
                .containsExactly(queryStrings(DATABASE, "SELECT CAST(COUNT(*) AS CHAR) FROM user").get(0));
    }

    @Test
    void migration_stopsBeforeDdlWhenLegacyWholesaleFieldsExist() throws Exception {
        loadSnapshot(DATABASE);
        execute(DATABASE,
                "ALTER TABLE `user` ADD COLUMN `user_group` TINYINT NOT NULL DEFAULT 1",
                "ALTER TABLE `sku` ADD COLUMN `wholesale_price` DECIMAL(10,2) NULL",
                "ALTER TABLE `order_item` ADD COLUMN `price_type` TINYINT NOT NULL DEFAULT 1");

        ExecResult migration = runSqlFile(DATABASE, "/tmp/pricing-release.sql");

        assertThat(migration.getExitCode()).isNotZero();
        assertThat(migration.getStdout() + migration.getStderr())
                .contains("legacy wholesale pricing fields");
        assertThat(tableExists(DATABASE, "user_level")).isFalse();
        assertThat(columnExists(DATABASE, "user", "level_id")).isFalse();
    }

    @Test
    void migration_rejectsPartiallyAppliedTargetSchema() throws Exception {
        loadSnapshot(DATABASE);
        execute(DATABASE, "ALTER TABLE `user` ADD COLUMN `level_id` BIGINT UNSIGNED NOT NULL DEFAULT 1");

        ExecResult migration = runSqlFile(DATABASE, "/tmp/pricing-release.sql");

        assertThat(migration.getExitCode()).isNotZero();
        assertThat(migration.getStdout() + migration.getStderr())
                .contains("already contains target pricing schema");
        assertThat(tableExists(DATABASE, "user_level")).isFalse();
    }

    @Test
    void freshInitAndReleaseMigrationProduceTheSamePricingModel() throws Exception {
        String migrated = "hardware_mall_migrated";
        String initialized = "hardware_mall_initialized";
        loadSnapshot(migrated);
        copyModifiedResource(INIT_RESOURCE, "/tmp/init-final.sql", DATABASE, initialized);
        copyModifiedResource(BASE_MIGRATION_RESOURCE, "/tmp/migration-base-final.sql", null, null);
        copyModifiedResource(RATE_MIGRATION_RESOURCE, "/tmp/migration-rate-final.sql", null, null);

        assertThat(runSqlFile(null, "/tmp/init-final.sql").getExitCode()).isZero();
        assertThat(runSqlFile(migrated, "/tmp/migration-base-final.sql").getExitCode()).isZero();
        assertThat(runSqlFile(migrated, "/tmp/migration-rate-final.sql").getExitCode()).isZero();

        assertThat(pricingModelDefinition(initialized))
                .isEqualTo(pricingModelDefinition(migrated));
        assertFinalPricingModel(initialized);
        assertFinalPricingModel(migrated);
    }

    @Test
    void rateRenameMigrationPreservesPricesAndClearsLegacyRates() throws Exception {
        loadSnapshot(DATABASE);
        assertThat(runSqlFile(DATABASE, "/tmp/pricing-release.sql").getExitCode()).isZero();
        execute(DATABASE,
                "INSERT INTO category (id, parent_id, name, sort_order, status) " +
                        "VALUES (1, 0, '测试分类', 0, 1)",
                "INSERT INTO level_category_discount (level_id, category_id, rate) " +
                        "VALUES (1, 1, 0.8000)",
                "INSERT INTO spu (id, category_id, name, original_price) " +
                        "VALUES (1, 1, '价格快照 SPU', 199.00)",
                "INSERT INTO sku (id, spu_id, specs, price, spec_hash) " +
                        "VALUES (1, 1, JSON_ARRAY(), 188.00, 'price-snapshot')");

        List<String> skuPrices = queryStrings(DATABASE,
                "SELECT CONCAT(id, ':', price) FROM sku ORDER BY id");
        List<String> spuPrices = queryStrings(DATABASE,
                "SELECT CONCAT(id, ':', original_price) FROM spu ORDER BY id");

        ExecResult migration = runSqlFile(DATABASE, "/tmp/pricing-rate-rename.sql");

        assertThat(migration.getExitCode())
                .as(migration.getStdout() + migration.getStderr())
                .isZero();
        assertThat(queryStrings(DATABASE,
                "SELECT CONCAT(id, ':', price) FROM sku ORDER BY id"))
                .isEqualTo(skuPrices);
        assertThat(queryStrings(DATABASE,
                "SELECT CONCAT(id, ':', original_price) FROM spu ORDER BY id"))
                .isEqualTo(spuPrices);
        assertThat(tableExists(DATABASE, "level_category_discount")).isFalse();
        assertThat(tableExists(DATABASE, "level_category_rate")).isTrue();
        assertThat(columnExists(DATABASE, "order_item", "discount_rate")).isFalse();
        assertThat(columnExists(DATABASE, "order_item", "pricing_rate")).isTrue();
        assertThat(queryStrings(DATABASE, "SELECT CAST(COUNT(*) AS CHAR) FROM level_category_rate"))
                .containsExactly("0");
    }

    private static void assertFinalPricingModel(String schema) throws SQLException {
        assertThat(tableExists(schema, "user_level")).isTrue();
        assertThat(tableExists(schema, "level_category_rate")).isTrue();
        assertThat(tableExists(schema, "level_category_discount")).isFalse();
        assertThat(columnExists(schema, "user", "level_id")).isTrue();
        assertThat(columnExists(schema, "spu", "exclude_discount")).isTrue();
        assertThat(columnExists(schema, "order_item", "level_id")).isTrue();
        assertThat(columnExists(schema, "order_item", "level_name")).isTrue();
        assertThat(columnExists(schema, "order_item", "pricing_rate")).isTrue();
        assertThat(columnExists(schema, "order_item", "discount_rate")).isFalse();
        assertThat(columnExists(schema, "user", "user_group")).isFalse();
        assertThat(columnExists(schema, "sku", "wholesale_price")).isFalse();
        assertThat(columnExists(schema, "order_item", "price_type")).isFalse();

        assertThat(queryStrings(schema,
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'level_id' " +
                        "AND is_nullable = 'NO' AND column_default = '1'"))
                .containsExactly("level_id");
        assertThat(queryStrings(schema,
                "SELECT index_name FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = 'level_category_rate' " +
                        "AND index_name = 'uk_level_category' AND non_unique = 0"))
                .containsOnly("uk_level_category");
        assertThat(queryStrings(schema,
                "SELECT constraint_name FROM information_schema.table_constraints " +
                        "WHERE constraint_schema = DATABASE() AND table_name = 'level_category_rate' " +
                        "AND constraint_type = 'FOREIGN KEY' ORDER BY constraint_name"))
                .containsExactly("fk_lcr_category", "fk_lcr_level");
    }

    private static List<String> pricingModelDefinition(String schema) throws SQLException {
        List<String> definition = new ArrayList<>(queryStrings(schema,
                "SELECT CONCAT(table_name, '.', column_name, ':', column_type, ':', is_nullable, ':', " +
                        "COALESCE(column_default, '<null>')) " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name IN " +
                "('user', 'spu', 'sku', 'order_item', 'user_level', 'level_category_rate') " +
                        "ORDER BY table_name, ordinal_position"));
        definition.addAll(queryStrings(schema,
                "SELECT CONCAT(table_name, '.', index_name, ':', non_unique, ':', seq_in_index, ':', column_name) " +
                        "FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name IN " +
                "('user', 'spu', 'sku', 'order_item', 'user_level', 'level_category_rate') " +
                        "ORDER BY table_name, index_name, seq_in_index"));
        return definition;
    }

    private static void loadSnapshot(String schema) throws Exception {
        copyModifiedResource(SNAPSHOT_RESOURCE, "/tmp/snapshot-" + schema + ".sql", DATABASE, schema);
        ExecResult result = runSqlFile(null, "/tmp/snapshot-" + schema + ".sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void runMigrations(String schema) throws Exception {
        ExecResult baseMigration = runSqlFile(schema, "/tmp/pricing-release.sql");
        assertThat(baseMigration.getExitCode())
                .as(baseMigration.getStdout() + baseMigration.getStderr())
                .isZero();

        ExecResult rateMigration = runSqlFile(schema, "/tmp/pricing-rate-rename.sql");
        assertThat(rateMigration.getExitCode())
                .as(rateMigration.getStdout() + rateMigration.getStderr())
                .isZero();
    }

    private static ExecResult runSqlFile(String schema, String path) throws Exception {
        if (schema == null) {
            return mysql("-e", "SOURCE " + path);
        }
        return mysql(schema, "-e", "SOURCE " + path);
    }

    private static ExecResult mysql(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("mysql");
        command.add("--default-character-set=utf8mb4");
        command.add("-uroot");
        command.add("-p" + MYSQL.getPassword());
        command.addAll(List.of(args));
        return MYSQL.execInContainer(command.toArray(String[]::new));
    }

    private static void execute(String schema, String... sql) throws SQLException {
        try (Connection connection = open(schema); Statement statement = connection.createStatement()) {
            for (String command : sql) {
                statement.execute(command);
            }
        }
    }

    private static boolean tableExists(String schema, String table) throws SQLException {
        return !queryStrings(schema,
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")
                .isEmpty();
    }

    private static boolean columnExists(String schema, String table, String column) throws SQLException {
        return !queryStrings(schema,
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = '" + table + "' " +
                        "AND column_name = '" + column + "'")
                .isEmpty();
    }

    private static List<String> queryStrings(String schema, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = open(schema);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static Connection open(String schema) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + schema +
                        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                "root",
                MYSQL.getPassword());
    }

    private static void copyResource(String resource, String destination) throws IOException {
        try (var input = new ClassPathResource(resource).getInputStream()) {
            MYSQL.copyFileToContainer(Transferable.of(input.readAllBytes()), destination);
        }
    }

    private static void copyModifiedResource(
            String resource,
            String destination,
            String sourceDatabase,
            String targetDatabase) throws IOException {
        try (var input = new ClassPathResource(resource).getInputStream()) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (sourceDatabase != null) {
                sql = sql.replace("`" + sourceDatabase + "`", "`" + targetDatabase + "`");
            }
            MYSQL.copyFileToContainer(
                    Transferable.of(sql.getBytes(StandardCharsets.UTF_8)),
                    destination);
        }
    }
}
