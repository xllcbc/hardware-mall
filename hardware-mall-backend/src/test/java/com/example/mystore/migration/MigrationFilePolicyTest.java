package com.example.mystore.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationFilePolicyTest {

    private static final Path MIGRATION_DIR = Path.of("..", "db", "migration");
    private static final Path V1 = MIGRATION_DIR.resolve("V1__baseline.sql");

    @Test
    void everyMigration_mustNotCreateOrSwitchDatabase() throws IOException {
        for (Path migration : migrationFiles()) {
            for (String line : Files.readAllLines(migration)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                String upper = trimmed.toUpperCase(Locale.ROOT);
                assertThat(upper)
                        .as("%s must not create or switch databases", migration.getFileName())
                        .doesNotContain("CREATE DATABASE");
                assertThat(upper)
                        .as("%s must not switch databases", migration.getFileName())
                        .doesNotStartWith("USE ");
                assertThat(upper)
                        .as("%s must not switch databases", migration.getFileName())
                        .doesNotStartWith("USE`");
            }
        }
    }

    @Test
    void baseline_mustCreateTablesIdempotently() throws IOException {
        for (String line : Files.readAllLines(V1)) {
            String upper = line.stripLeading().toUpperCase(Locale.ROOT);
            if (upper.startsWith("CREATE TABLE")) {
                assertThat(upper).contains("IF NOT EXISTS");
            }
        }
    }

    private static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files
                    .filter(path -> path.getFileName().toString().matches("V[^_]+__.*\\.sql"))
                    .sorted()
                    .toList();
        }
    }
}
