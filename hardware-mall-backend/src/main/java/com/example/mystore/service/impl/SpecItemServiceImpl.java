package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.mapper.SpecItemMapper;
import com.example.mystore.service.SpecItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpecItemServiceImpl implements SpecItemService {

    private final SpecItemMapper specItemMapper;

    @Override
    public List<SpecItem> getItemsByTemplate(Long templateId) {
        LambdaQueryWrapper<SpecItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecItem::getTemplateId, templateId)
               .eq(SpecItem::getDeleteTime, 0)
               .orderByAsc(SpecItem::getSortOrder);
        return specItemMapper.selectList(wrapper);
    }

    @Override
    public List<SpecItem> getItemsByCategory(Long categoryId) {
        LambdaQueryWrapper<SpecItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecItem::getDeleteTime, 0)
               .orderByAsc(SpecItem::getSortOrder);
        return specItemMapper.selectList(wrapper);
    }

    @Override
    public SpecItem createItem(SpecItem item) {
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleteTime(0L);
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        specItemMapper.insert(item);
        return item;
    }

    @Override
    public SpecItem updateItem(SpecItem item) {
        SpecItem exist = specItemMapper.selectById(item.getId());
        if (exist == null) {
            throw new RuntimeException("规格项不存在");
        }

        if (item.getValue() != null) {
            exist.setValue(item.getValue());
        }
        if (item.getSortOrder() != null) {
            exist.setSortOrder(item.getSortOrder());
        }
        exist.setUpdateTime(LocalDateTime.now());
        specItemMapper.updateById(exist);
        return exist;
    }

    @Override
    public void deleteItem(Long id) {
        SpecItem item = specItemMapper.selectById(id);
        if (item == null) {
            throw new RuntimeException("规格项不存在");
        }
        item.setDeleteTime(System.currentTimeMillis());
        specItemMapper.updateById(item);
    }

    @Override
    public Map<Long, List<SpecItem>> getItemsGroupedByTemplateIds(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SpecItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SpecItem::getTemplateId, templateIds)
               .eq(SpecItem::getDeleteTime, 0)
               .orderByAsc(SpecItem::getSortOrder);
        List<SpecItem> items = specItemMapper.selectList(wrapper);
        return items.stream().collect(Collectors.groupingBy(SpecItem::getTemplateId));
    }
}
