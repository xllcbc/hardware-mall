package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Spu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpuPaginationMySqlTest {

    private static final long CATEGORY_ID = 8801L;
    private static final long PRODUCT_ID_BASE = 880100L;
    private static final int PRODUCT_COUNT = 125;
    private static final int PAGE_SIZE = 20;
    private static final LocalDateTime SORT_TIME = LocalDateTime.of(2026, 9, 1, 18, 7, 5);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("hardware_mall")
            .withUsername("test")
            .withPassword("pagination-test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "db", "migration", "V1__baseline.sql")),
                    "/docker-entrypoint-initdb.d/01-v1-baseline.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private SpuService spuService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedProductsWithIdenticalSortKeys() {
        jdbcTemplate.update(
                "INSERT INTO category (id, parent_id, name, sort_order, status, delete_time) VALUES (?, ?, ?, ?, ?, ?)",
                CATEGORY_ID, 0, "分页测试分类", 10, 1, 0);

        List<Object[]> rows = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            rows.add(new Object[]{
                    PRODUCT_ID_BASE + i,
                    CATEGORY_ID,
                    "并列排序商品" + i,
                    BigDecimal.TEN,
                    0,
                    1,
                    0,
                    0,
                    Timestamp.valueOf(SORT_TIME),
                    Timestamp.valueOf(SORT_TIME),
                    0
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO spu (id, category_id, name, original_price, sales_count, status, " +
                        "is_recommend, exclude_discount, create_time, update_time, delete_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rows);
    }

    @Test
    void categoryPaginationDoesNotRepeatOrSkipProductsWithIdenticalSortKeys() {
        List<Long> expectedIds = LongStream.rangeClosed(1, PRODUCT_COUNT)
                .map(i -> PRODUCT_ID_BASE + i)
                .boxed()
                .toList();

        for (int pass = 0; pass < 5; pass++) {
            List<Long> actualIds = traverseAllPages();

            assertThat(actualIds)
                    .as("pagination pass %s", pass)
                    .hasSize(PRODUCT_COUNT)
                    .doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(expectedIds);
        }
    }

    private List<Long> traverseAllPages() {
        List<Long> ids = new ArrayList<>();
        int pageCount = (PRODUCT_COUNT + PAGE_SIZE - 1) / PAGE_SIZE;
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            Page<Spu> page = spuService.getSpuPage(CATEGORY_ID, null, pageNumber, PAGE_SIZE, null);

            assertThat(page.getTotal()).isEqualTo(PRODUCT_COUNT);
            ids.addAll(page.getRecords().stream().map(Spu::getId).toList());
        }
        return ids;
    }
}
