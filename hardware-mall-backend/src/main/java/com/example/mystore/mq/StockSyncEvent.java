package com.example.mystore.mq;

import lombok.Getter;
import java.util.List;

/**
 * 库存同步事件
 * 在事务提交后触发，将数据库最新库存同步到 Redis
 */
@Getter
public class StockSyncEvent {
    private final List<Long> skuIds;

    public StockSyncEvent(List<Long> skuIds) {
        this.skuIds = skuIds;
    }
}
