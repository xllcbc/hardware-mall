package com.example.mystore.entity.vo;

import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductDetailVO implements Serializable {
    private Spu spu;
    private List<Sku> skus;
    private List<SpecTemplate> specTemplates;
    private Map<Long, List<SpecItem>> specItemsMap;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    public ProductDetailVO() {}

    public ProductDetailVO(Spu spu, List<Sku> skus, List<SpecTemplate> specTemplates, Map<Long, List<SpecItem>> specItemsMap, BigDecimal minPrice, BigDecimal maxPrice) {
        this.spu = spu;
        this.skus = skus;
        this.specTemplates = specTemplates;
        this.specItemsMap = specItemsMap;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }
}