package com.example.mystore.service;

import com.example.mystore.entity.db.SpecItem;
import java.util.List;
import java.util.Map;

public interface SpecItemService {
    List<SpecItem> getItemsByTemplate(Long templateId);
    List<SpecItem> getItemsByCategory(Long categoryId);
    SpecItem createItem(SpecItem item);
    SpecItem updateItem(SpecItem item);
    void deleteItem(Long id);
    Map<Long, List<SpecItem>> getItemsGroupedByTemplateIds(List<Long> templateIds);
}
