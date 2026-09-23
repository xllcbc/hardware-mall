package com.example.mystore.service;

import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.entity.vo.ProductDetailCache;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ProductCacheService {

    ProductListResult getProductList(Long categoryId, int page, int limit, int status,
                                     Supplier<ProductListResult> loader);

    ProductListResult searchProducts(String keyword, Long categoryId, int page, int limit, int status,
                                     Supplier<ProductListResult> loader);

    void invalidateAfterSkuChange(Long spuId, Long skuId);

    void invalidateAfterStockChange(Long skuId);

    void invalidateAfterSpuChange(Long spuId, boolean recommendChanged);

    ProductDetailCache getProductDetail(Long spuId, Supplier<ProductDetailCache> loader);

    ProductListResult getRecommend(Supplier<ProductListResult> loader);

    List<Sku> getSkusByIds(List<Long> skuIds, Function<List<Long>, List<Sku>> loader);
}
