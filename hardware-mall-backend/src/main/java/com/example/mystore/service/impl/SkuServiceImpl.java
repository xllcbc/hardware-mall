package com.example.mystore.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.vo.SpecVO;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpecItemMapper;
import com.example.mystore.mapper.SpecTemplateMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final SpecTemplateMapper specTemplateMapper;
    private final SpecItemMapper specItemMapper;
    private final SpecTemplateService specTemplateService;
    private final SpecItemService specItemService;
    private final RedisUtil redisUtil;

    @Override
    public Page<Sku> getSkuPage(Long spuId, Integer page, Integer limit) {
        Page<Sku> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getSpuId, spuId)
               .eq(Sku::getDeleteTime, 0)
               .orderByDesc(Sku::getCreateTime);
        return skuMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Sku getSkuById(Long id) {
        Sku sku = redisUtil.queryWithCache(
                RedisConstants.PREFIX_SKU_INFO + id, Sku.class, RedisConstants.CACHE_TTL_HOUR,
                () -> {
                    Sku s = skuMapper.selectById(id);
                    if (s == null || s.getDeleteTime() != 0) {
                        return null;
                    }
                    s.setStock(null);
                    return s;
                });
        if (sku == null) {
            throw new RuntimeException("SKU不存在");
        }
        sku.setStock(getStockById(id));
        return sku;
    }

    @Override
    public Integer getStockById(Long skuId) {
        Object cached = redisUtil.get(RedisConstants.PREFIX_SKU_STOCK + skuId);
        if (cached != null && !redisUtil.isNull(cached)) {
            return Integer.parseInt(cached.toString());
        }
        Sku sku = skuMapper.selectById(skuId);
        return sku == null ? null : sku.getStock();
    }

    @Override
    public List<Sku> getSkusBySpu(Long spuId) {
        return getSkusBySpu(spuId, 1);
    }

    @Override
    public List<Sku> getSkusBySpu(Long spuId, Integer status) {
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getSpuId, spuId)
               .eq(Sku::getDeleteTime, 0);
        if (status != null) {
            wrapper.eq(Sku::getStatus, status);
        }
        return skuMapper.selectList(wrapper);
    }

    @Override
    public Map<Long, Long> countBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return new HashMap<>();
        }
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Sku::getSpuId, spuIds)
               .eq(Sku::getDeleteTime, 0);
        return skuMapper.selectList(wrapper).stream()
                .filter(sku -> sku.getDeleteTime() == null || sku.getDeleteTime() == 0L)
                .collect(Collectors.groupingBy(Sku::getSpuId, Collectors.counting()));
    }

    @Override
    public Sku getSkuBySpecs(Long spuId, List<SpecVO> specs) {
        List<Sku> skus = getSkusBySpu(spuId);
        for (Sku sku : skus) {
            if (matchSpecs(sku.getSpecs(), specs)) {
                return sku;
            }
        }
        return null;
    }

    private boolean matchSpecs(List<SpecVO> skuSpecs, List<SpecVO> selectedSpecs) {
        if (skuSpecs == null || selectedSpecs == null) {
            return false;
        }
        for (SpecVO selected : selectedSpecs) {
            boolean found = false;
            for (SpecVO skuSpec : skuSpecs) {
                if (skuSpec.getTemplateId().equals(selected.getTemplateId())
                    && skuSpec.getItemId().equals(selected.getItemId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public Sku createSku(Sku sku) {
        Spu spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null) {
            throw new RuntimeException("商品不存在");
        }

        sku.setSpecHash(computeSpecHash(sku.getSpecs()));
        sku.setCreateTime(LocalDateTime.now());
        sku.setUpdateTime(LocalDateTime.now());
        sku.setDeleteTime(0L);
        sku.setStatus(1);
        if (sku.getStock() == null) {
            sku.setStock(0);
        }
        skuMapper.insert(sku);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + sku.getSpuId());
        syncStockToCache(sku.getId());
        return sku;
    }

    @Override
    @Transactional
    public Sku updateSku(Sku sku) {
        Sku exist = skuMapper.selectById(sku.getId());
        if (exist == null) {
            throw new RuntimeException("SKU不存在");
        }

        if (sku.getSpecs() != null) {
            exist.setSpecs(sku.getSpecs());
            exist.setSpecHash(computeSpecHash(sku.getSpecs()));
        }
        if (sku.getPrice() != null) {
            exist.setPrice(sku.getPrice());
        }
        if (sku.getStock() != null) {
            exist.setStock(sku.getStock());
        }
        if (sku.getImage() != null) {
            exist.setImage(sku.getImage());
        }
        if (sku.getStatus() != null) {
            exist.setStatus(sku.getStatus());
        }
        exist.setUpdateTime(LocalDateTime.now());
        skuMapper.updateById(exist);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + exist.getSpuId());
        redisUtil.delete(RedisConstants.PREFIX_SKU_INFO + exist.getId());
        syncStockToCache(exist.getId());
        return exist;
    }

    @Override
    public void deleteSku(Long id) {
        Sku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new RuntimeException("SKU不存在");
        }
        Long spuId = sku.getSpuId();
        sku.setDeleteTime(System.currentTimeMillis());
        skuMapper.updateById(sku);
        redisUtil.delete(RedisConstants.PREFIX_PRODUCT_DETAIL + spuId);
        redisUtil.delete(RedisConstants.PREFIX_SKU_INFO + id);
    }

    @Override
    @Transactional
    public boolean deductStock(Long skuId, Integer quantity) {
        int rows = skuMapper.deductStock(skuId, quantity);
        return rows > 0;
    }

    @Override
    @Transactional
    public void restoreStock(Long skuId, Integer quantity) {
        skuMapper.restoreStock(skuId, quantity);
    }

    @Override
    @Transactional
    public void generateSkusByTemplate(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new RuntimeException("商品不存在");
        }

        List<SpecTemplate> templates = specTemplateService.getTemplatesByCategory(spu.getCategoryId());
        if (templates.isEmpty()) {
            return;
        }

        List<List<SpecItem>> specItemLists = new ArrayList<>();
        for (SpecTemplate template : templates) {
            List<SpecItem> items = specItemService.getItemsByTemplate(template.getId());
            specItemLists.add(items);
        }

        List<List<SpecItem>> combinations = cartesianProduct(specItemLists);

        for (List<SpecItem> combination : combinations) {
            List<SpecVO> specs = new ArrayList<>();
            for (SpecItem item : combination) {
                SpecVO specVO = new SpecVO();
                specVO.setTemplateId(item.getTemplateId());
                specVO.setItemId(item.getId());
                specVO.setName(getTemplateName(templates, item.getTemplateId()));
                specVO.setValue(item.getValue());
                specs.add(specVO);
            }

            Sku sku = new Sku();
            sku.setSpuId(spuId);
            sku.setSpecs(specs);
            sku.setPrice(spu.getOriginalPrice() != null ? spu.getOriginalPrice() : BigDecimal.ZERO);
            sku.setStock(0);
            sku.setStatus(1);
            createSku(sku);
        }
    }

    @Override
    public boolean syncStockToCache(Long skuId) {
        try {
            Sku sku = skuMapper.selectById(skuId);
            if (sku != null && sku.getStock() != null) {
                redisUtil.set(RedisConstants.PREFIX_SKU_STOCK + skuId, sku.getStock());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("库存缓存同步失败, skuId={}", skuId, e);
            return false;
        }
    }

    // 已删除 deductStockFromCache / restoreStockToCache
    // 库存缓存改为事务提交后由 OrderServiceImpl 统一调用 syncStockToCache() 同步

    private String getTemplateName(List<SpecTemplate> templates, Long templateId) {
        for (SpecTemplate template : templates) {
            if (template.getId().equals(templateId)) {
                return template.getName();
            }
        }
        return "";
    }

    private String computeSpecHash(List<SpecVO> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        List<SpecVO> sortedSpecs = new ArrayList<>(specs);
        sortedSpecs.sort(Comparator.comparingLong(SpecVO::getTemplateId));
        String json = JSON.toJSONString(sortedSpecs);
        return md5(json);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    private <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> result = new ArrayList<>();
        if (lists.isEmpty()) {
            result.add(new ArrayList<>());
            return result;
        }
        cartesianHelper(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private <T> void cartesianHelper(List<List<T>> lists, int index, List<T> current, List<List<T>> result) {
        if (index == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (T item : lists.get(index)) {
            current.add(item);
            cartesianHelper(lists, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    //    private List<SpecVO> toSpecVOList(Object specs) {
//        if (specs == null) return null;
//        List<SpecVO> result = new ArrayList<>();
//        for (Object item : (List<?>) specs) {
//            if (item instanceof SpecVO) {
//                result.add((SpecVO) item);
//            } else if (item instanceof Map) {
//                Map<?, ?> map = (Map<?, ?>) item;
//                SpecVO vo = new SpecVO();
//                vo.setTemplateId(toLong(map.get("templateId")));
//                vo.setItemId(toLong(map.get("itemId")));
//                vo.setName((String) map.get("name"));
//                vo.setValue((String) map.get("value"));
//                result.add(vo);
//            }
//        }
//        return result;
//    }
//
//    private Long toLong(Object value) {
//        if (value == null) return null;
//        if (value instanceof Long) return (Long) value;
//        if (value instanceof Number) return ((Number) value).longValue();
//        return Long.parseLong(value.toString());
//    }
}
