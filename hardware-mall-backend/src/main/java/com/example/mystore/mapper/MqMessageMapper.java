package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.MqMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表 Mapper
 */
@Mapper
public interface MqMessageMapper extends BaseMapper<MqMessage> {

    /**
     * 查询待发送的消息（带LIMIT防止OOM）
     */
    @Select("SELECT * FROM mq_message WHERE status = 0 ORDER BY create_time ASC LIMIT #{limit}")
    List<MqMessage> selectPendingMessages(@Param("limit") int limit);

    /**
     * 查询滞留消息（待发送且创建时间超过指定分钟数，带LIMIT）
     */
    @Select("SELECT * FROM mq_message WHERE status = 0 AND create_time < #{beforeTime} ORDER BY create_time ASC LIMIT #{limit}")
    List<MqMessage> selectStaleMessages(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);

    /**
     * 删除已消费超过指定时间前的消息
     * @param beforeTime 截止时间
     * @return 删除条数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM mq_message WHERE status = 3 AND create_time < #{beforeTime}")
    int deleteConsumedBefore(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 查询卡住的发送中消息（status=4 且 update_time 超时）
     */
    @Select("SELECT * FROM mq_message WHERE status = 4 AND update_time < #{beforeTime} ORDER BY update_time ASC LIMIT #{limit}")
    List<MqMessage> selectStuckSendingMessages(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);
}
