package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT COUNT(*) FROM shop_order WHERE pay_time >= #{todayStart} " +
            "AND status NOT IN (1, 5, 7)")
    Long countTodayPaidOrders(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM shop_order WHERE pay_time >= #{todayStart} " +
            "AND status NOT IN (1, 5, 7)")
    BigDecimal sumTodaySales(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 查询超期的待付款订单（用于兜底定时任务）
     * @param status 订单状态（待付款）
     * @param beforeTime 创建时间小于此时间的订单
     * @param limit 单次查询上限
     */
    @Select("SELECT * FROM shop_order " +
            "WHERE status = #{status} AND create_time < #{beforeTime} " +
            "ORDER BY create_time ASC " +
            "LIMIT #{limit}")
    List<Order> selectStalePendingOrders(@Param("status") Integer status,
                                          @Param("beforeTime") LocalDateTime beforeTime,
                                          @Param("limit") Integer limit);
}
