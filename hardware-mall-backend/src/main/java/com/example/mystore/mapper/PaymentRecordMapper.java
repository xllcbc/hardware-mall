package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 退款对账扫描: 退款中的支付记录(不限订单状态) + 已支付且订单已取消的支付记录。
     * 以支付记录为入口, 覆盖已取消订单的迟到支付退款。
     */
    String SELECT_STALE_REFUND_RECORDS =
            "SELECT pr.* FROM payment_record pr "
                    + "JOIN shop_order o ON o.id = pr.order_id "
                    + "WHERE (pr.status = " + PaymentRecord.STATUS_REFUNDING
                    + " OR (pr.status = " + PaymentRecord.STATUS_PAID
                    + " AND o.status = " + StatusConstants.ORDER_CANCELLED + ")) "
                    + "AND pr.update_time < #{beforeTime} "
                    + "ORDER BY pr.update_time ASC "
                    + "LIMIT #{limit}";

    @Select(SELECT_STALE_REFUND_RECORDS)
    List<PaymentRecord> selectStaleRefundRecords(@Param("beforeTime") LocalDateTime beforeTime,
                                                 @Param("limit") Integer limit);
}
