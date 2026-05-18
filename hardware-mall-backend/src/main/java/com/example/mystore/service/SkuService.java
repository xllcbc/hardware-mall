package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.SpecVO;
import java.util.List;

public interface SkuService {
    Page<Sku> getSkuPage(Long spuId, Integer page, Integer limit);
    Sku getSkuById(Long id);
    List<Sku> getSkusBySpu(Long spuId);
    List<Sku> getSkusBySpu(Long spuId, Integer status);
    Sku getSkuBySpecs(Long spuId, List<SpecVO> specs);
    Sku createSku(Sku sku);
    Sku updateSku(Sku sku);
    void deleteSku(Long id);
    boolean deductStock(Long skuId, Integer quantity);
    void restoreStock(Long skuId, Integer quantity);
    void generateSkusByTemplate(Long spuId);
    boolean syncStockToCache(Long skuId);
}
