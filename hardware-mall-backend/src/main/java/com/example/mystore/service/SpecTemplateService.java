package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.SpecTemplate;
import java.util.List;

public interface SpecTemplateService {
    Page<SpecTemplate> getTemplatePage(Long categoryId, String name, Integer page, Integer limit);
    SpecTemplate getTemplateById(Long id);
    List<SpecTemplate> getTemplatesByCategory(Long categoryId);
    SpecTemplate createTemplate(SpecTemplate template);
    SpecTemplate updateTemplate(SpecTemplate template);
    void deleteTemplate(Long id);
}
