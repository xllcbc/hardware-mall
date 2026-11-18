package com.example.mystore.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationFilePolicyTest {

    private static final Path V1 = Path.of("..", "db", "migration", "V1__baseline.sql");

    @Test
    void baseline_mustNotCreateOrSwitchDatabase() throws IOException {
        for (String line : Files.readAllLines(V1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            assertThat(upper).doesNotContain("CREATE DATABASE");
            assertThat(upper).doesNotStartWith("USE ");
            assertThat(upper).doesNotStartWith("USE`");
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
}
