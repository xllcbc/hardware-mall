package com.example.mystore.entity.vo;

import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.db.Spu;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ProductDetailCache implements Serializable {
    private Spu spu;
    private List<Sku> skus;
    private List<SpecTemplate> specTemplates;
    private Map<Long, List<SpecItem>> specItemsMap;
}
