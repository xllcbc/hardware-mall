package com.example.mystore.common.exception;

import com.example.mystore.entity.vo.CheckoutPreviewVO;
import com.example.mystore.entity.vo.PriceChangeVO;

import java.math.BigDecimal;

public class PriceChangedException extends BusinessException {

    public static final int CODE = 40901;

    private final PriceChangeVO priceChange;

    public PriceChangedException(BigDecimal expectedPayAmount, CheckoutPreviewVO preview) {
        super(CODE, "商品价格已变化，请确认后重新提交");
        this.priceChange = new PriceChangeVO(expectedPayAmount, preview);
    }

    public PriceChangeVO getPriceChange() {
        return priceChange;
    }
}
