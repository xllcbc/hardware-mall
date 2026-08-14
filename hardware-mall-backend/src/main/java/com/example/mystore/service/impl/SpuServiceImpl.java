package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.SpuService;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.util.RedisUtil;
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
            throw new RuntimeException("商品不存在或已下架");
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
        return spu;
    }

    @Override
    public Spu updateSpu(Spu spu) {
        Spu exist = spuMapper.selectById(spu.getId());
        if (exist == null) {
            throw new RuntimeException("商品不存在");
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
        return exist;
    }

    @Override
    public void deleteSpu(Long id) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new RuntimeException("商品不存在");
        }
        spu.setDeleteTime(System.currentTimeMillis());
        spuMapper.updateById(spu);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + id);
        if (spu.getIsRecommend() != null && spu.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Spu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new RuntimeException("商品不存在");
        }
        spu.setStatus(status);
        spu.setUpdateTime(LocalDateTime.now());
        spuMapper.updateById(spu);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + id);
        if (spu.getIsRecommend() != null && spu.getIsRecommend() == 1) {
            redisUtil.delete(RedisConstants.PREFIX_PRODUCT_RECOMMEND);
        }
    }

    @Override
    public Long getTotalCount() {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getStatus, StatusConstants.PRODUCT_STATUS_ON_SHELF)
               .eq(Spu::getDeleteTime, 0);
        return spuMapper.selectCount(wrapper);
    }

    @Override
    public Page<ProductListVO> getProductListVO(Long categoryId, String keyword, Integer page, Integer limit, Integer status) {
        Page<Spu> spuPage = getSpuPage(categoryId, keyword, page, limit, status);
        Page<ProductListVO> voPage = new Page<>(spuPage.getCurrent(), spuPage.getSize(), spuPage.getTotal());

        List<ProductListVO> voList = spuPage.getRecords().stream().map(spu -> {
            ProductListVO vo = convertToProductListVO(spu);
            List<Sku> skus = skuService.getSkusBySpu(spu.getId(), 1);
            if (skus != null && !skus.isEmpty()) {
                BigDecimal minPrice = skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                BigDecimal maxPrice = skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
                vo.setMinPrice(minPrice);
                vo.setMaxPrice(maxPrice);
            } else {
                vo.setMinPrice(spu.getOriginalPrice());
                vo.setMaxPrice(spu.getOriginalPrice());
            }
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<ProductListVO> getRecommendProductListVO(Integer limit) {
        @SuppressWarnings("unchecked")
        List<ProductListVO> voList = (List<ProductListVO>) redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRODUCT_RECOMMEND, List.class, RedisConstants.CACHE_TTL_HOUR,
                () -> buildRecommendVO(limit));
        if (voList == null) {
            return Collections.emptyList();
        }
        if (limit != null && voList.size() > limit) {
            return voList.subList(0, limit);
        }
        return voList;
    }

    private List<ProductListVO> buildRecommendVO(Integer limit) {
        List<Spu> spus = getRecommendSpus();
        if (spus.isEmpty()) {
            return null;
        }
        if (limit != null && spus.size() > limit) {
            spus = spus.subList(0, limit);
        }
        return spus.stream().map(spu -> {
            ProductListVO vo = convertToProductListVO(spu);
            List<Sku> skus = skuService.getSkusBySpu(spu.getId(), 1);
            if (skus != null && !skus.isEmpty()) {
                vo.setMinPrice(skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice()));
                vo.setMaxPrice(skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice()));
            } else {
                vo.setMinPrice(spu.getOriginalPrice());
                vo.setMaxPrice(spu.getOriginalPrice());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public ProductDetailVO getProductDetailVO(Long id) {
        ProductDetailVO vo = redisUtil.queryWithCache(
                RedisConstants.PREFIX_PRODUCT_DETAIL + id, ProductDetailVO.class, RedisConstants.CACHE_TTL_HOUR,
                () -> buildProductDetailVO(id));
        if (vo == null) {
            throw new RuntimeException("商品不存在或已下架");
        }
        return vo;
    }

    private ProductDetailVO buildProductDetailVO(Long id) {
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

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if (skus != null && !skus.isEmpty()) {
            minPrice = skus.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
            maxPrice = skus.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(spu.getOriginalPrice());
        }

        return new ProductDetailVO(spu, skus, specTemplates, specItemsMap, minPrice, maxPrice);
    }

    private ProductListVO convertToProductListVO(Spu spu) {
        ProductListVO vo = new ProductListVO();
        vo.setId(spu.getId());
        vo.setCategoryId(spu.getCategoryId());
        vo.setName(spu.getName());
        vo.setSubtitle(spu.getSubtitle());
        vo.setImages(spu.getImages());
        vo.setOriginalPrice(spu.getOriginalPrice());
        vo.setStatus(spu.getStatus());
        return vo;
    }
}
