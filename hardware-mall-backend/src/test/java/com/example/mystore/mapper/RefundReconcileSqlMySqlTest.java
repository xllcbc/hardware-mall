package com.example.mystore.mapper;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.PaymentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 MySQL 验证退款对账扫描 SQL（非 H2）。
 */
@Testcontainers
@Tag("integration")
class RefundReconcileSqlMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("hardware_mall")
            .withUsername("test")
            .withPassword("test");

    private static final String QUERY = PaymentRecordMapper.SELECT_STALE_REFUND_RECORDS
            .replace("#{beforeTime}", "?")
            .replace("#{limit}", "?");

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS payment_record");
            statement.execute("DROP TABLE IF EXISTS shop_order");
            statement.execute("CREATE TABLE shop_order (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, status TINYINT NOT NULL) ENGINE=InnoDB");
            statement.execute("CREATE TABLE payment_record (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "order_id BIGINT NOT NULL, status TINYINT NOT NULL, " +
                    "update_time DATETIME NOT NULL, KEY idx_order_id (order_id)) ENGINE=InnoDB");
        }
    }

    @Test
    void shouldSelectRefundingAndPaidCancelledWithinGraceOrderedAndLimited() throws SQLException {
        long cancelledOrder = insertOrder(StatusConstants.ORDER_CANCELLED);
        long pendingShipmentOrder = insertOrder(StatusConstants.ORDER_PENDING_SHIPMENT);
        long refundingOrder = insertOrder(StatusConstants.ORDER_REFUNDING);
        LocalDateTime now = LocalDateTime.now();

        long paidCancelled = insertRecord(cancelledOrder, PaymentRecord.STATUS_PAID, now.minusHours(2));
        long refundingNormal = insertRecord(pendingShipmentOrder, PaymentRecord.STATUS_REFUNDING, now.minusHours(1));
        long refundingCancelled = insertRecord(cancelledOrder, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(30));
        // 符合条件但排序靠后, 被 limit 截断
        insertRecord(refundingOrder, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(20));
        // 以下均不应命中
        insertRecord(pendingShipmentOrder, PaymentRecord.STATUS_PAID, now.minusHours(1));
        insertRecord(cancelledOrder, PaymentRecord.STATUS_REFUNDED, now.minusHours(1));
        insertRecord(pendingShipmentOrder, PaymentRecord.STATUS_REFUNDING, now);

        List<Long> ids = query(now.minusMinutes(10), 3);

        assertThat(ids).containsExactly(paidCancelled, refundingNormal, refundingCancelled);
    }

    @Test
    void shouldApplyLimitAfterOrdering() throws SQLException {
        long cancelledOrder = insertOrder(StatusConstants.ORDER_CANCELLED);
        LocalDateTime now = LocalDateTime.now();
        long second = insertRecord(cancelledOrder, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(50));
        long first = insertRecord(cancelledOrder, PaymentRecord.STATUS_REFUNDING, now.minusMinutes(60));

        List<Long> ids = query(now.minusMinutes(10), 1);

        assertThat(ids).containsExactly(first);
        assertThat(ids).doesNotContain(second);
    }

    private List<Long> query(LocalDateTime beforeTime, int limit) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setTimestamp(1, Timestamp.valueOf(beforeTime));
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getLong("id"));
                }
            }
        }
        return ids;
    }

    private long insertOrder(int status) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO shop_order (status) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, status);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private long insertRecord(long orderId, int status, LocalDateTime updateTime) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO payment_record (order_id, status, update_time) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, orderId);
            statement.setInt(2, status);
            statement.setTimestamp(3, Timestamp.valueOf(updateTime));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
