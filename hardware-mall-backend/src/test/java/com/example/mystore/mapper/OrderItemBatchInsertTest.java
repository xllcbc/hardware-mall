package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.entity.db.Cart;
import com.example.mystore.entity.db.Order;
import com.example.mystore.entity.db.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/schema-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
class OrderItemBatchInsertTest {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CartMapper cartMapper;

    @Test
    void insertBatch_shouldInsertAllItems() {
        Order order = new Order();
        order.setOrderNo("SO20260101000001");
        order.setUserId(2L);
        order.setAddressId(1L);
        order.setLogisticsId(1L);
        order.setStatus(1);
        order.setTotalAmount(new BigDecimal("438.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("438.00"));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);

        OrderItem item1 = new OrderItem();
        item1.setOrderId(order.getId());
        item1.setSkuId(1L);
        item1.setSpuId(1L);
        item1.setProductName("防盗门锁 C级");
        item1.setPrice(new BigDecimal("219.00"));
        item1.setQuantity(1);
        item1.setSubtotal(new BigDecimal("219.00"));
        item1.setCreateTime(LocalDateTime.now());

        OrderItem item2 = new OrderItem();
        item2.setOrderId(order.getId());
        item2.setSkuId(2L);
        item2.setSpuId(1L);
        item2.setProductName("防盗门锁 C级-升级款");
        item2.setPrice(new BigDecimal("219.00"));
        item2.setQuantity(1);
        item2.setSubtotal(new BigDecimal("219.00"));
        item2.setCreateTime(LocalDateTime.now());

        int rows = orderItemMapper.insertBatch(Arrays.asList(item1, item2));

        assertThat(rows).isEqualTo(2);
        List<OrderItem> items = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()));
        assertThat(items).hasSize(2);
        assertThat(items).extracting(OrderItem::getProductName)
                .contains("防盗门锁 C级", "防盗门锁 C级-升级款");
    }

    @Test
    void batchCartSoftDelete_shouldMarkMatchedRows() {
        Cart c1 = new Cart();
        c1.setUserId(2L);
        c1.setSkuId(1L);
        c1.setQuantity(1);
        c1.setDeleteTime(0L);
        cartMapper.insert(c1);

        Cart c2 = new Cart();
        c2.setUserId(2L);
        c2.setSkuId(2L);
        c2.setQuantity(1);
        c2.setDeleteTime(0L);
        cartMapper.insert(c2);

        Cart c3 = new Cart();
        c3.setUserId(3L);
        c3.setSkuId(1L);
        c3.setQuantity(1);
        c3.setDeleteTime(0L);
        cartMapper.insert(c3);

        long now = System.currentTimeMillis();
        LambdaUpdateWrapper<Cart> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Cart::getUserId, 2L)
               .in(Cart::getSkuId, Arrays.asList(1L, 2L))
               .set(Cart::getDeleteTime, now);
        int rows = cartMapper.update(null, wrapper);

        assertThat(rows).isEqualTo(2);

        List<Cart> all = cartMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, 2L));
        assertThat(all).allMatch(c -> c.getDeleteTime() > 0);

        List<Cart> other = cartMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, 3L));
        assertThat(other).allMatch(c -> c.getDeleteTime() == 0L);
    }
}
