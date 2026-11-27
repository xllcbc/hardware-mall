package com.example.mystore.service;

import com.example.mystore.entity.dto.CheckoutPreviewRequest;
import com.example.mystore.entity.dto.CreateOrderRequest;
import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.OrderVO;

/**
 * 订单接入：预览与创建的唯一编排入口。
 *
 * <p>地址与物流校验、一次批量等级定价与订单等级快照、应付金额校验、幂等认领与失败标记都在这里；
 * 创建在事务内扣库存与销量、写订单与明细、删购物车、幂等记录置成功并发布库存缓存事件。
 * 金额口径继续由等级定价模块与金额换算模块承担。
 */
public interface OrderIntakeService {

    /**
     * 结算预览：与创建共用同一份批量等级定价结果，只读不落库。
     *
     * @throws com.example.mystore.common.exception.BusinessException 商品不可售、库存不足或金额低于 0.01 元
     */
    CheckoutPreviewVO previewOrder(CheckoutPreviewRequest request);

    /**
     * 创建订单：幂等认领 → 地址/物流校验 → 定价快照 → 应付金额校验 → 事务内落库并置幂等记录成功 → 组装视图。
     *
     * <p>提交前失败会把幂等记录置失败; 提交后组装视图失败不回滚已成功的订单与幂等记录。
     */
    OrderVO createOrder(Long userId, CreateOrderRequest request, String idempotencyKey);
}
