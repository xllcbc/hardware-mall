package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.OrderIdempotency;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderIdempotencyMapper extends BaseMapper<OrderIdempotency> {

    default OrderIdempotency selectByUserAndKey(Long userId, String idemKey) {
        return selectOne(new LambdaQueryWrapper<OrderIdempotency>()
                .eq(OrderIdempotency::getUserId, userId)
                .eq(OrderIdempotency::getIdemKey, idemKey)
                .last("LIMIT 1"));
    }

    /**
     * FAILED → PROCESSING 重新认领；只有仍是 FAILED 时才能成功，避免覆盖并发请求。
     */
    @Update("UPDATE order_idempotency SET state = '" + OrderIdempotency.STATE_PROCESSING + "', "
            + "owner_token = #{ownerToken}, request_hash = #{requestHash}, order_id = NULL, "
            + "update_time = #{now} "
            + "WHERE id = #{id} AND state = '" + OrderIdempotency.STATE_FAILED + "'")
    int claimFailedRecord(@Param("id") Long id,
                          @Param("ownerToken") String ownerToken,
                          @Param("requestHash") String requestHash,
                          @Param("now") LocalDateTime now);

    /**
     * 回收超时 PROCESSING：必须仍由旧 owner 持有且超过宽限时间，旧持有者之后无法再覆盖。
     */
    @Update("UPDATE order_idempotency SET state = '" + OrderIdempotency.STATE_PROCESSING + "', "
            + "owner_token = #{ownerToken}, request_hash = #{requestHash}, order_id = NULL, "
            + "update_time = #{now} "
            + "WHERE id = #{id} AND state = '" + OrderIdempotency.STATE_PROCESSING + "' "
            + "AND owner_token = #{previousOwnerToken} AND update_time < #{cutoff}")
    int takeOverExpiredProcessing(@Param("id") Long id,
                                  @Param("previousOwnerToken") String previousOwnerToken,
                                  @Param("ownerToken") String ownerToken,
                                  @Param("requestHash") String requestHash,
                                  @Param("cutoff") LocalDateTime cutoff,
                                  @Param("now") LocalDateTime now);

    @Update("UPDATE order_idempotency SET state = '" + OrderIdempotency.STATE_SUCCEEDED + "', "
            + "order_id = #{orderId}, update_time = #{now} "
            + "WHERE id = #{id} AND state = '" + OrderIdempotency.STATE_PROCESSING + "' "
            + "AND owner_token = #{ownerToken}")
    int markSucceeded(@Param("id") Long id,
                      @Param("ownerToken") String ownerToken,
                      @Param("orderId") Long orderId,
                      @Param("now") LocalDateTime now);

    @Update("UPDATE order_idempotency SET state = '" + OrderIdempotency.STATE_FAILED + "', "
            + "update_time = #{now} "
            + "WHERE id = #{id} AND state = '" + OrderIdempotency.STATE_PROCESSING + "' "
            + "AND owner_token = #{ownerToken}")
    int markFailed(@Param("id") Long id,
                   @Param("ownerToken") String ownerToken,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE order_idempotency SET state = '" + OrderIdempotency.STATE_FAILED + "', "
            + "update_time = #{now} "
            + "WHERE state = '" + OrderIdempotency.STATE_PROCESSING + "' AND update_time < #{cutoff}")
    int reclaimExpiredProcessing(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("now") LocalDateTime now);

    @Delete("DELETE FROM order_idempotency "
            + "WHERE state IN ('" + OrderIdempotency.STATE_SUCCEEDED + "', '"
            + OrderIdempotency.STATE_FAILED + "') AND update_time < #{cutoff}")
    int deleteSettledBefore(@Param("cutoff") LocalDateTime cutoff);
}
