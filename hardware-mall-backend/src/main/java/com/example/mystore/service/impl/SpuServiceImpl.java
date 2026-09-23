package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductDetailCache;
import com.example.mystore.entity.vo.ProductListCacheItem;
import com.example.mystore.entity.vo.ProductListResult;
import com.example.mystore.entity.vo.ProductSkuVO;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.PriceService;
import com.example.mystore.service.ProductCacheService;
import com.example.mystore.service.SpuService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.mystore.entity.vo.SpecVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SkuService skuService;
    private final SpecTemplateService specTemplateService;
    private final SpecItemService specItemService;
    private final PriceService priceService;
    private final ProductCacheService productCacheService;
    private final RedisUtil redisUtil;

    @Override
    public Page<Spu> getSpuPage(Long categoryId, String keyword, Integer page, Integer limit, Integer status) {
        Page<Spu> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getDeleteTime, 0);
        if (status != null) {
            wrapper.eq(Spu::getStatus, status);
        }

        if (categoryId != null && categoryId > 0) {
            wrapper.eq(Spu::getCategoryId, categoryId);
        }

        if (StringUtils.hasText(keyword)) {
            if (keyword.matches("[\u4e00-\u9fa5]+")) {
                wrapper.apply("name LIKE CONCAT('%', {0}, '%')",
                        String.join("%", keyword.split("")));
            } else {
                wrapper.like(Spu::getName, keyword);
            }
        }

        wrapper.orderByDesc(Spu::getSalesCount, Spu::getCreateTime);
        return spuMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<Spu> getRecommendSpus() {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getStatus, StatusConstants.PRODUCT_STATUS_ON_SHELF)
               .eq(Spu::getIsRecommend, 1)
               .eq(Spu::getDeleteTime, 0)
               .orderByDesc(Spu::getSalesCount)
               .last("LIMIT 10");
        return spuMapper.selectList(wrapper);
    }

    @Override
    public Spu getSpuById(Long id) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null || spu.getDeleteTime() != 0) {
            throw new BusinessException("商品不存在或已下架");
        }
        return spu;
    }

    @Override
    public Spu createSpu(Spu spu) {
        spu.setCreateTime(LocalDateTime.now());
        spu.setUpdateTime(LocalDateTime.now());
        spu.setSalesCount(0);
        spu.setDeleteTime(0L);
        if (spu.getStatus() == null) {
            spu.setStatus(StatusConstants.PRODUCT_STATUS_ON_SHELF);
        }
        if (spu.getIsRecommend() == null) {
            spu.setIsRecommend(0);
        }
        spuMapper.insert(spu);
        if (spu.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        return spu;
    }

    @Override
    public Spu updateSpu(Spu spu) {
        Spu exist = spuMapper.selectById(spu.getId());
        if (exist == null) {
            throw new BusinessException("商品不存在");
        }

        if (StringUtils.hasText(spu.getName())) {
            exist.setName(spu.getName());
        }
        if (spu.getSubtitle() != null) {
            exist.setSubtitle(spu.getSubtitle());
        }
        if (spu.getDescription() != null) {
            exist.setDescription(spu.getDescription());
        }
        if (spu.getImages() != null) {
            exist.setImages(spu.getImages());
        }
        if (spu.getOriginalPrice() != null) {
            exist.setOriginalPrice(spu.getOriginalPrice());
        }
        if (spu.getCategoryId() != null) {
            exist.setCategoryId(spu.getCategoryId());
        }
        if (spu.getStatus() != null) {
            exist.setStatus(spu.getStatus());
        }
        if (spu.getIsRecommend() != null) {
            exist.setIsRecommend(spu.getIsRecommend());
        }
        if (spu.getWeight() != null) {
            exist.setWeight(spu.getWeight());
        }
        exist.setUpdateTime(LocalDateTime.now());

        spuMapper.updateById(exist);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + spu.getId());
        if (exist.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
        return exist;
    }

    @Override
    public void deleteSpu(Long id) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        spu.setDeleteTime(System.currentTimeMillis());
        spuMapper.updateById(spu);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + id);
        if (spu.getIsRecommend() != null && spu.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        spu.setStatus(status);
        spu.setUpdateTime(LocalDateTime.now());
        spuMapper.updateById(spu);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + id);
        if (spu.getIsRecommend() != null && spu.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
        redisUtil.deleteByPattern(RedisConstants.PREFIX_PRODUCT_LIST + "*");
    }

    @Override
    public Long getTotalCount() {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getStatus, StatusConstants.PRODUCT_STATUS_ON_SHELF)
               .eq(Spu::getDeleteTime, 0);
        return spuMapper.selectCount(wrapper);
    }

    @Override
    public Long countCreatedBetween(LocalDateTime start, LocalDateTime end) {
        // M11: 上新量 —— 按创建时间统计, 含未上架商品
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Spu::getCreateTime, start)
               .lt(Spu::getCreateTime, end)
               .eq(Spu::getDeleteTime, 0);
        return spuMapper.selectCount(wrapper);
    }

    @Override
    public Page<ProductListVO> getProductListVO(Long categoryId, String keyword, Integer page, Integer limit, Integer status) {
        if (!StringUtils.hasText(keyword)) {
            ProductListResult result = productCacheService.getProductList(
                    categoryId, page, limit, status,
                    () -> buildProductListResult(categoryId, null, page, limit, status));
            if (result == null || result.getRecords() == null) {
                return new Page<>(page, limit, 0);
            }
            Page<ProductListVO> voPage = new Page<>(page, limit, result.getTotal());
            voPage.setRecords(toUserPricingViews(result.getRecords()));
            return voPage;
        }
        ProductListResult result = productCacheService.searchProducts(
                keyword, categoryId, page, limit, status,
                () -> buildProductListResult(categoryId, keyword, page, limit, status));
        if (result == null || result.getRecords() == null) {
            return new Page<>(page, limit, 0);
        }
        Page<ProductListVO> voPage = new Page<>(page, limit, result.getTotal());
        voPage.setRecords(toUserPricingViews(result.getRecords()));
        return voPage;
    }

    private Page<ProductListCacheItem> buildProductListCachePage(Long categoryId, String keyword, Integer page, Integer limit, Integer status) {
        Page<Spu> spuPage = getSpuPage(categoryId, keyword, page, limit, status);
        Page<ProductListCacheItem> cachePage = new Page<>(spuPage.getCurrent(), spuPage.getSize(), spuPage.getTotal());

        List<Long> spuIds = spuPage.getRecords().stream().map(Spu::getId).collect(Collectors.toList());
        Map<Long, List<Sku>> skusBySpu = skuService.getSkusBySpuIds(spuIds);

        List<ProductListCacheItem> cacheItems = spuPage.getRecords().stream().map(spu -> {
            ProductListCacheItem item = convertToProductListCacheItem(spu);
            List<Sku> skus = skusBySpu.getOrDefault(spu.getId(), Collections.emptyList());
            if (!skus.isEmpty()) {
                BigDecimal minPrice = skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                BigDecimal maxPrice = skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                item.setRetailMinPrice(minPrice);
                item.setRetailMaxPrice(maxPrice);
                applyWholesaleEffectiveRange(item, skus);
            } else {
                item.setRetailMinPrice(spu.getOriginalPrice());
                item.setRetailMaxPrice(spu.getOriginalPrice());
            }
            return item;
        }).collect(Collectors.toList());

        cachePage.setRecords(cacheItems);
        return cachePage;
    }

    private ProductListResult buildProductListResult(Long categoryId, String keyword, Integer page, Integer limit, Integer status) {
        Page<ProductListCacheItem> cachePage = buildProductListCachePage(categoryId, keyword, page, limit, status);
        return new ProductListResult(cachePage.getRecords(), cachePage.getTotal());
    }

    @Override
    public List<ProductListVO> getRecommendProductListVO(Integer limit) {
        ProductListResult result = productCacheService.getRecommend(() -> buildRecommendResult(limit));
        if (result == null || result.getRecords() == null) {
            return Collections.emptyList();
        }
        List<ProductListVO> userPricingViews = toUserPricingViews(result.getRecords());
        if (limit != null && userPricingViews.size() > limit) {
            return userPricingViews.subList(0, limit);
        }
        return userPricingViews;
    }

    private ProductListResult buildRecommendResult(Integer limit) {
        List<Spu> spus = getRecommendSpus();
        if (spus.isEmpty()) {
            return null;
        }
        if (limit != null && spus.size() > limit) {
            spus = spus.subList(0, limit);
        }
        List<Long> spuIds = spus.stream().map(Spu::getId).collect(Collectors.toList());
        Map<Long, List<Sku>> skusBySpu = skuService.getSkusBySpuIds(spuIds);
        List<ProductListCacheItem> cacheItems = spus.stream().map(spu -> {
            ProductListCacheItem item = convertToProductListCacheItem(spu);
            List<Sku> skus = skusBySpu.getOrDefault(spu.getId(), Collections.emptyList());
            if (!skus.isEmpty()) {
                item.setRetailMinPrice(skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice()));
                item.setRetailMaxPrice(skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice()));
                applyWholesaleEffectiveRange(item, skus);
            } else {
                item.setRetailMinPrice(spu.getOriginalPrice());
                item.setRetailMaxPrice(spu.getOriginalPrice());
            }
            return item;
        }).collect(Collectors.toList());
        return new ProductListResult(cacheItems, cacheItems.size());
    }

    @Override
    public ProductDetailVO getProductDetailVO(Long id) {
        ProductDetailCache cache = productCacheService.getProductDetail(id, () -> buildProductDetailCache(id));
        if (cache == null) {
            throw new BusinessException("商品不存在或已下架");
        }
        return toProductDetailView(cache);
    }

    @Override
    public List<ProductSkuVO> getProductSkuVOs(Long spuId) {
        return toProductSkuVOs(skuService.getSkusBySpu(spuId, 1));
    }

    private ProductDetailCache buildProductDetailCache(Long id) {
        Spu spu;
        try {
            spu = getSpuById(id);
        } catch (RuntimeException e) {
            return null;
        }

        List<Sku> skus = skuService.getSkusBySpu(id, 1);
        List<SpecTemplate> specTemplates = specTemplateService.getTemplatesByCategory(spu.getCategoryId());
        List<Long> templateIds = specTemplates.stream().map(SpecTemplate::getId).collect(Collectors.toList());
        Map<Long, List<SpecItem>> specItemsMap = specItemService.getItemsGroupedByTemplateIds(templateIds);

        if (skus != null && !skus.isEmpty()) {
            Set<Long> usedItemIds = skus.stream()
                    .flatMap(sku -> sku.getSpecs().stream())
                    .map(SpecVO::getItemId)
                    .collect(Collectors.toSet());

            Map<Long, List<SpecItem>> filteredMap = new HashMap<>();
            for (Map.Entry<Long, List<SpecItem>> entry : specItemsMap.entrySet()) {
                List<SpecItem> filtered = entry.getValue().stream()
                        .filter(item -> usedItemIds.contains(item.getId()))
                        .collect(Collectors.toList());
                if (!filtered.isEmpty()) {
                    filteredMap.put(entry.getKey(), filtered);
                }
            }
            specItemsMap = filteredMap;

            Set<Long> usedTemplateIds = specItemsMap.keySet();
            specTemplates = specTemplates.stream()
                    .filter(t -> usedTemplateIds.contains(t.getId()))
                    .collect(Collectors.toList());
        } else {
            specTemplates = Collections.emptyList();
            specItemsMap = Collections.emptyMap();
        }

        if (skus != null) {
            skus.forEach(sku -> sku.setStock(null));
        }

        ProductDetailCache cache = new ProductDetailCache();
        cache.setSpu(spu);
        cache.setSkus(skus);
        cache.setSpecTemplates(specTemplates);
        cache.setSpecItemsMap(specItemsMap);
        return cache;
    }

    private ProductDetailVO toProductDetailView(ProductDetailCache cache) {
        List<ProductSkuVO> skus = toProductSkuVOs(cache.getSkus());
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if (!skus.isEmpty()) {
            minPrice = skus.stream().map(ProductSkuVO::getPrice).min(BigDecimal::compareTo)
                    .orElse(cache.getSpu().getOriginalPrice());
            maxPrice = skus.stream().map(ProductSkuVO::getPrice).max(BigDecimal::compareTo)
                    .orElse(cache.getSpu().getOriginalPrice());
        }
        return new ProductDetailVO(cache.getSpu(), skus, cache.getSpecTemplates(),
                cache.getSpecItemsMap(), minPrice, maxPrice);
    }

    private List<ProductSkuVO> toProductSkuVOs(List<Sku> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyList();
        }
        Integer userGroup = StatusConstants.normalizeUserGroup(UserContext.getUserGroup());
        return skus.stream().map(sku -> {
            PriceService.PriceResult priceResult = priceService.resolve(sku, userGroup);
            ProductSkuVO vo = new ProductSkuVO();
            vo.setId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSpecs(sku.getSpecs());
            vo.setPrice(priceResult.unitPrice());
            vo.setPriceType(priceResult.priceType());
            vo.setStock(skuService.getStockById(sku.getId()));
            vo.setImage(sku.getImage());
            vo.setStatus(sku.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    private ProductListCacheItem convertToProductListCacheItem(Spu spu) {
        ProductListCacheItem item = new ProductListCacheItem();
        item.setId(spu.getId());
        item.setCategoryId(spu.getCategoryId());
        item.setName(spu.getName());
        item.setSubtitle(spu.getSubtitle());
        item.setImages(spu.getImages());
        item.setOriginalPrice(spu.getOriginalPrice());
        item.setStatus(spu.getStatus());
        return item;
    }

    private void applyWholesaleEffectiveRange(ProductListCacheItem item, List<Sku> skus) {
        boolean hasWholesalePrice = skus.stream().anyMatch(sku -> sku.getWholesalePrice() != null);
        if (!hasWholesalePrice) {
            return;
        }
        List<BigDecimal> effectivePrices = skus.stream()
                .map(sku -> priceService.resolve(sku, StatusConstants.USER_GROUP_WHOLESALE).unitPrice())
                .collect(Collectors.toList());
        item.setWholesaleMinPrice(effectivePrices.stream().min(BigDecimal::compareTo).orElse(null));
        item.setWholesaleMaxPrice(effectivePrices.stream().max(BigDecimal::compareTo).orElse(null));
    }

    private List<ProductListVO> toUserPricingViews(List<ProductListCacheItem> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Integer userGroup = StatusConstants.normalizeUserGroup(UserContext.getUserGroup());
        return records.stream().map(source -> {
            ProductListVO target = new ProductListVO();
            target.setId(source.getId());
            target.setCategoryId(source.getCategoryId());
            target.setName(source.getName());
            target.setSubtitle(source.getSubtitle());
            target.setImages(source.getImages());
            target.setOriginalPrice(source.getOriginalPrice());
            target.setStatus(source.getStatus());
            if (userGroup == StatusConstants.USER_GROUP_WHOLESALE
                    && source.getWholesaleMinPrice() != null) {
                target.setMinPrice(source.getWholesaleMinPrice());
                target.setMaxPrice(source.getWholesaleMaxPrice());
                target.setPriceType(StatusConstants.PRICE_TYPE_WHOLESALE);
            } else {
                target.setMinPrice(source.getRetailMinPrice());
                target.setMaxPrice(source.getRetailMaxPrice());
                target.setPriceType(StatusConstants.PRICE_TYPE_RETAIL);
            }
            return target;
        }).collect(Collectors.toList());
    }
}
