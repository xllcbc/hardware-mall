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

    // ===== M11: Dashboard 涨幅统计(口径与上方今日统计一致) =====

    @Select("SELECT COUNT(*) FROM shop_order WHERE pay_time >= #{start} AND pay_time < #{end} " +
            "AND status NOT IN (1, 5, 7)")
    Long countPaidOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM shop_order WHERE pay_time >= #{start} AND pay_time < #{end} " +
            "AND status NOT IN (1, 5, 7)")
    BigDecimal sumSalesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM shop_order WHERE ship_time >= #{start} AND ship_time < #{end}")
    Long countShippedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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

    /**
     * 查询发货超期未收货的订单（用于自动收货定时任务）
     * @param status 订单状态（已发货）
     * @param beforeTime 发货时间小于此时间的订单
     * @param limit 单次查询上限
     */
    @Select("SELECT * FROM shop_order " +
            "WHERE status = #{status} AND ship_time < #{beforeTime} " +
            "ORDER BY ship_time ASC " +
            "LIMIT #{limit}")
    List<Order> selectStaleShippedOrders(@Param("status") Integer status,
                                          @Param("beforeTime") LocalDateTime beforeTime,
                                          @Param("limit") Integer limit);

    /**
     * 查询滞留的退款中订单（用于退款对账兜底任务）
     * 按 update_time 过滤, 覆盖"已发起退款但结果未知"的滞留单
     * @param status 订单状态（退款中）
     * @param beforeTime 更新时间小于此时间的订单
     * @param limit 单次查询上限
     */
    @Select("SELECT * FROM shop_order " +
            "WHERE status = #{status} AND update_time < #{beforeTime} " +
            "ORDER BY update_time ASC " +
            "LIMIT #{limit}")
    List<Order> selectStaleRefundingOrders(@Param("status") Integer status,
                                            @Param("beforeTime") LocalDateTime beforeTime,
                                            @Param("limit") Integer limit);

    /**
     * 查询已发货但未上报微信发货信息的订单(同城/自提), 用于上报重试任务
     */
    @Select("SELECT * FROM shop_order " +
            "WHERE status = #{status} AND delivery_type IN (2, 4) " +
            "AND wechat_order_state IS NULL AND ship_time < #{beforeTime} " +
            "ORDER BY ship_time ASC " +
            "LIMIT #{limit}")
    List<Order> selectUnreportedShippedOrders(@Param("status") Integer status,
                                               @Param("beforeTime") LocalDateTime beforeTime,
                                               @Param("limit") Integer limit);

    /**
     * 查询已上报微信(wechat_order_state=2)待收敛的已发货订单, 用于对账
     */
    @Select("SELECT * FROM shop_order " +
            "WHERE status = #{status} AND wechat_order_state = 2 AND ship_time < #{beforeTime} " +
            "ORDER BY ship_time ASC " +
            "LIMIT #{limit}")
    List<Order> selectReconcileShippedOrders(@Param("status") Integer status,
                                              @Param("beforeTime") LocalDateTime beforeTime,
                                              @Param("limit") Integer limit);
}
